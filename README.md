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

- El job `semgrep` sube SARIF a la pestaña Security, así que el workflow que llama debe conceder `security-events: write`. En un repositorio privado hace falta tener habilitado Code Security.
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

## Validación local de los workflows

```bash
pip install actionlint-py yamllint
actionlint .github/workflows/seguridad.yml
yamllint .github/workflows/seguridad.yml
```

## Costos

Usa runners hospedados por GitHub. En repositorios públicos los minutos de Actions son gratuitos; en privados cuentan contra la cuota del plan. No crea recursos en ninguna nube.
