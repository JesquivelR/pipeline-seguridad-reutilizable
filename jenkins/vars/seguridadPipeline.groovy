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
//   sonarServidor nombre del servidor SonarQube configurado en Jenkins (por defecto sonarqube)

import seguridad.Lenguajes

def call(Map config = [:]) {
    def lenguaje = config.get('lenguaje', 'java')
    def ruta = config.get('ruta', '.')
    def umbralCvss = config.get('umbralCvss', '7')

    def sonarServidor = config.get('sonarServidor', 'sonarqube')

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

            stage('Gitleaks') {
                steps {
                    dir(ruta) {
                        sh '''
                            docker run --rm -v "$PWD:/repo" zricethezav/gitleaks:v8.18.4 \
                                detect --source /repo --redact --exit-code 1 \
                                --report-format sarif --report-path /repo/gitleaks.sarif
                        '''
                    }
                }
                post {
                    always { archiveArtifacts artifacts: "${ruta}/gitleaks.sarif", allowEmptyArchive: true }
                }
            }

            stage('SonarQube') {
                steps {
                    dir(ruta) {
                        withSonarQubeEnv(sonarServidor) {
                            sh 'sonar-scanner -Dsonar.projectKey=${JOB_NAME//\\//_} -Dsonar.sources=.'
                        }
                    }
                }
            }

            stage('Quality gate') {
                steps {
                    timeout(time: 10, unit: 'MINUTES') {
                        waitForQualityGate abortPipeline: true
                    }
                }
            }

            stage('OWASP Dependency-Check') {
                steps {
                    dir(ruta) {
                        dependencyCheck additionalArguments: "--scan . --format HTML --format JSON --failOnCVSS ${umbralCvss}",
                                        odcInstallation: 'dependency-check'
                    }
                }
                post {
                    always { dependencyCheckPublisher pattern: "${ruta}/dependency-check-report.json" }
                }
            }
        }
    }
}
