# Pipeline de seguridad reutilizable

[![Ejemplos](https://github.com/JesquivelR/pipeline-seguridad-reutilizable/actions/workflows/ejemplos.yml/badge.svg?branch=main)](https://github.com/JesquivelR/pipeline-seguridad-reutilizable/actions/workflows/ejemplos.yml)
[![Seguridad](https://github.com/JesquivelR/pipeline-seguridad-reutilizable/actions/workflows/seguridad.yml/badge.svg?branch=main)](https://github.com/JesquivelR/pipeline-seguridad-reutilizable/actions/workflows/seguridad.yml)

Workflow reutilizable de GitHub Actions que revisa un proyecto en cuatro frentes: secretos, código, dependencias e infraestructura como código. Se escribe una vez aquí y se consume desde cualquier otro repositorio con unas pocas líneas.

En este avance están los cuatro jobs de análisis. SBOM, escaneo de imágenes y firma quedan para una etapa posterior.

## Arquitectura

```
 repositorio consumidor                 pipeline-seguridad-reutilizable
 ----------------------                 -------------------------------
 .github/workflows/ci.yml   workflow_call   .github/workflows/seguridad.yml
        |  uses: ...@main  ------------->        |
        |  with: lenguaje, ruta, umbral          +--> gitleaks          (secretos en el historial)
                                                 +--> Semgrep           (SAST, SARIF a Security)
                                                 +--> Dependency-Check  (CVE en dependencias, caché NVD)
                                                 +--> Trivy             (fs: vuln y secretos; config: IaC y Dockerfile)
                                                         |
                                                         v
                                                 reportes como artefactos del run
```

Los cuatro jobs corren en paralelo. Si cualquiera encuentra un problema por encima de su umbral, el job falla y con él el workflow que lo llamó.

## Jobs

| Job | Qué revisa | Cuándo falla | Reporte |
|-----|-----------|--------------|---------|
| `gitleaks` | Secretos en todo el historial de git | Cualquier secreto detectado | `gitleaks-sarif` |
| `semgrep` | Código fuente con reglas según el lenguaje | Cualquier hallazgo de las reglas elegidas | `semgrep-sarif` y pestaña Security |
| `dependency-check` | Dependencias con CVE conocidos | CVSS igual o mayor a `umbral-cvss` | `dependency-check` (HTML y SARIF) |
| `trivy` | Dependencias, secretos, Terraform, Kubernetes y Dockerfile | Severidad HIGH o CRITICAL | `trivy-config-sarif` |

Los binarios que se descargan (gitleaks, Dependency-Check) se verifican con sha256 y las acciones están fijadas por SHA de commit.

## Entradas y secretos

| Nombre | Tipo | Por defecto | Descripción |
|--------|------|-------------|-------------|
| `lenguaje` | entrada | `java` | `java`, `node`, `python`, `go` o `terraform`. Elige las reglas de Semgrep. Otro valor usa `p/default`. |
| `ruta` | entrada | `.` | Ruta del proyecto dentro del repositorio. |
| `umbral-cvss` | entrada | `7` | Puntaje CVSS (0 a 10) desde el cual falla Dependency-Check. |
| `NVD_API_KEY` | secreto, opcional | vacío | Llave de la API de la NVD. Acelera la primera descarga de la base. |

## Cómo consumirlo desde otro repositorio

Crea `.github/workflows/seguridad.yml` en el repositorio consumidor:

```yaml
name: Seguridad

on:
  pull_request:
  push:
    branches: [main]

permissions:
  contents: read
  security-events: write

jobs:
  seguridad:
    uses: JesquivelR/pipeline-seguridad-reutilizable/.github/workflows/seguridad.yml@main
    with:
      lenguaje: node
      ruta: .
      umbral-cvss: "7"
    secrets:
      NVD_API_KEY: ${{ secrets.NVD_API_KEY }}
```

Notas:

- El job `semgrep` sube SARIF a la pestaña Security, así que el workflow que llama debe conceder `security-events: write`. La subida solo se ejecuta cuando el repositorio es público: en uno privado se omite (code scanning no está disponible) y el SARIF queda igualmente como artefacto `semgrep-sarif`. La pestaña Security se llena cuando el repositorio es público.
- Para fijar una versión estable en lugar de `@main`, usa una etiqueta o el SHA de un commit.
- Si el repositorio que llama es privado, este repositorio debe permitir el acceso a sus workflows en Settings > Actions > General.

## Cómo probarlo

1. Crea un repositorio de prueba con un proyecto pequeño y el workflow de arriba.
2. Abre un pull request y revisa la pestaña Actions: deben aparecer los cuatro jobs.
3. Descarga los artefactos del run para ver los reportes completos.
4. Para comprobar que corta, agrega una dependencia con un CVE conocido y confirma que `dependency-check` falla con el umbral configurado.

## Rama de demostración con una dependencia vulnerable

La rama `demo/dependencia-vulnerable` parte de `main` y cambia una sola cosa en `ejemplos/node-api`: agrega `lodash` en la versión 4.17.15, que tiene CVE conocidos con CVSS por encima de 7 (por ejemplo CVE-2020-8203 y CVE-2021-23337). Con el umbral en `7`, el job `dependency-check` del workflow `Ejemplos` debe fallar y Trivy en modo filesystem debe listar los mismos hallazgos.

Esta rama nunca se fusiona a `main`. Existe para mostrar que el pipeline corta cuando aparece una dependencia con una vulnerabilidad alta.

Para reproducirlo en local:

```bash
git switch demo/dependencia-vulnerable
cd ejemplos/node-api
npm ci
npm audit --audit-level=high
```

Para ver el corte en CI, abre un pull request de esa rama hacia `main` sin fusionarlo, o lanza `Ejemplos` con `workflow_dispatch` sobre la rama, y revisa el job `dependency-check` y la pestaña Security.

## Versión para Jenkins (shared library)

La carpeta `jenkins/` contiene la misma revisión como shared library de Jenkins: el paso `seguridadPipeline` ejecuta las etapas en secuencia dentro de un solo pipeline declarativo.

```
jenkins/
  vars/seguridadPipeline.groovy    paso que consume el Jenkinsfile
  src/seguridad/Lenguajes.groovy   reglas y manifiesto por lenguaje
  resources/seguridad/             recursos de la librería
  local/                           Jenkins con Docker Compose y JCasC para probarla
```

### Registrar la librería en Jenkins

1. En Manage Jenkins > System > Global Pipeline Libraries, agrega una librería con el nombre `seguridad-pipeline`.
2. Elige Modern SCM con Git y la URL de este repositorio. Como la librería vive en `jenkins/` y no en la raíz, indica `jenkins` en el campo Library Path.
3. Fija una versión por defecto (rama, etiqueta o SHA). Para producción conviene una etiqueta y no `main`.
4. Instala los plugins `workflow-aggregator` (incluye Pipeline Groovy Libraries), `git`, `timestamps`, `sonar` y `dependency-check-jenkins-plugin`. La lista usada en local está en `jenkins/local/plugins.txt`.
5. Configura un servidor SonarQube con el nombre `sonarqube` y una instalación de Dependency-Check con el nombre `dependency-check`.
6. El agente necesita Docker para gitleaks y Trivy, y `sonar-scanner` en el PATH.

En el Jenkinsfile del proyecto:

```groovy
@Library('seguridad-pipeline') _

seguridadPipeline(
    lenguaje: 'java',
    ruta: 'ejemplos/java-api',
    umbralCvss: '7',
    sonarServidor: 'sonarqube',
    imagen: 'java-api-demo:local'
)
```

Todos los parámetros son opcionales. Si se omite `imagen`, solo se escanea el filesystem.

### Probarlo en local

```bash
cd jenkins/local
echo "JENKINS_ADMIN_PASSWORD=cambia-esto" > .env
docker compose up -d --build
```

Jenkins queda en `http://localhost:8080` (usuario `admin`) y SonarQube en `http://localhost:9000`. JCasC crea el job `java-api-seguridad`, que usa `ejemplos/java-api/Jenkinsfile`. Es un entorno de laboratorio: monta el socket de Docker y no debe exponerse a una red compartida. La imagen local trae Docker, pero no `sonar-scanner` ni una instalación de Dependency-Check: agrégalas en Manage Jenkins > Tools (o en el Dockerfile) antes de ejecutar el job completo, y crea un token en SonarQube para `SONAR_TOKEN`.

### Equivalencia con el workflow de GitHub Actions

| Etapa de Jenkins | Job de GitHub Actions | Diferencias |
|------------------|-----------------------|-------------|
| Gitleaks | `gitleaks` | Mismo escaneo del historial; el reporte SARIF se archiva como artefacto del build. |
| SonarQube y Quality gate | `semgrep` | Ambos son análisis estático del código. Jenkins usa SonarQube y corta con `waitForQualityGate abortPipeline: true`; Actions usa Semgrep con reglas por lenguaje. |
| OWASP Dependency-Check | `dependency-check` | El umbral `umbralCvss` equivale a `umbral-cvss` y se pasa como `--failOnCVSS`. Jenkins usa el plugin; Actions descarga el binario con checksum y cachea la base NVD. |
| Trivy filesystem | `trivy` (modo fs) | Ambos cortan en HIGH y CRITICAL. |
| Trivy imagen | no existe todavía | Se activa al pasar `imagen`; el escaneo de imágenes llegará al workflow con la cadena de suministro. |

El modo `config` de Trivy (Terraform, Kubernetes y Dockerfile) solo está en el workflow de Actions.

## Verificar la firma de una imagen

El workflow `firma-imagen.yml` firma el digest de la imagen sin llaves: Cosign pide a Sigstore un certificado efímero atado a la identidad OIDC del workflow que corre. Quien consume la imagen comprueba esa identidad, no una llave compartida.

En CI, el job `verificar-node-api` de `ejemplos.yml` llama a `verificar-firma.yml` después de firmar (solo en `main`) con la referencia por digest que devuelve el job de firma.

A mano, con Cosign 2.x instalado:

```bash
cosign verify \
  --certificate-identity "https://github.com/JesquivelR/pipeline-seguridad-reutilizable/.github/workflows/firma-imagen.yml@refs/heads/main" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  ghcr.io/jesquivelr/node-api@sha256:<digest>

cosign verify-attestation --type spdxjson \
  --certificate-identity "https://github.com/JesquivelR/pipeline-seguridad-reutilizable/.github/workflows/firma-imagen.yml@refs/heads/main" \
  --certificate-oidc-issuer "https://token.actions.githubusercontent.com" \
  ghcr.io/jesquivelr/node-api@sha256:<digest>
```

Qué se comprueba: que la firma existe en el registro, que el certificado lo emitió Sigstore para esa identidad exacta y que el evento quedó en el registro de transparencia Rekor. Si la identidad no coincide (otro repo, otro workflow u otra rama), el comando falla con código distinto de cero.

## Actualización de dependencias con Dependabot

El archivo `.github/dependabot.yml` revisa cada semana:

- las acciones de GitHub usadas en `.github/workflows` (agrupadas en un solo PR, y como están fijadas por versión, Dependabot propone el cambio de la versión);
- las dependencias de `ejemplos/node-api` (npm) y `ejemplos/java-api` (Maven);
- las imágenes base de los tres Dockerfile del repositorio.

La rama `demo/dependencia-vulnerable` no se toca: sirve para mostrar que el pipeline detecta una dependencia con CVE.

## Validación local de los workflows

```bash
pip install actionlint-py yamllint
actionlint .github/workflows/seguridad.yml
yamllint .github/workflows/seguridad.yml
```

## Costos

Usa runners hospedados por GitHub. En repositorios públicos los minutos de Actions son gratuitos; en privados cuentan contra la cuota del plan. No crea recursos en ninguna nube.
