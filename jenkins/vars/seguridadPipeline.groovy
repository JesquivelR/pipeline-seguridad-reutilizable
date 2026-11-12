// Paso de la shared library: pipeline de seguridad configurable por lenguaje.
//
// Uso en un Jenkinsfile:
//   @Library('seguridad-pipeline') _
//   seguridadPipeline(lenguaje: 'java', ruta: '.')
//
// Parametros (todos opcionales):
//   lenguaje   java, node, python, go o terraform (por defecto java)
//   ruta       carpeta del proyecto dentro del workspace (por defecto .)
//   umbralCvss puntaje CVSS a partir del cual falla el build (por defecto 7)

import seguridad.Lenguajes

def call(Map config = [:]) {
    def lenguaje = config.get('lenguaje', 'java')
    def ruta = config.get('ruta', '.')
    def umbralCvss = config.get('umbralCvss', '7')

    def perfil = Lenguajes.perfil(lenguaje)

    pipeline {
        agent any

        options {
            timestamps()
            timeout(time: 60, unit: 'MINUTES')
            disableConcurrentBuilds()
        }

        stages {
            stage('Descargar codigo') {
                steps {
                    checkout scm
                    echo "Lenguaje: ${lenguaje} | ruta: ${ruta} | umbral CVSS: ${umbralCvss} | reglas: ${perfil.reglas}"
                }
            }
        }
    }
}
