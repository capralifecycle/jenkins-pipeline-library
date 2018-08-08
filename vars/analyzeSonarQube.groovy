#!/usr/bin/groovy

/**
 * Analyze the project with SonarQube.
 *
 * Uses the configuration in Jenkins that points the the SonarQube
 * installation.
 */
def call(projectKey, Map params = [:]) {
  stage('Analyze with SonarQube') {
    withSonarQubeEnv {
      def img = docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/tool/sonar-scanner')
      img.pull() // ensure we have latest version
      img.inside {
        sh """
          sonar-scanner \\
            -Dsonar.projectKey='$projectKey' \\
            -Dsonar.sources='${params.get('sources', './src')}' \\
            -Dsonar.exclusions='${params.get('exclusions', '')}' \\
            -Dsonar.java.binaries='${params.get('java.binaries', '.')}'
          """
      }
    }
  }
}
