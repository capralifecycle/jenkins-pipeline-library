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
      docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/sonar-scanner').inside {
        sh """
          sonar-scanner \\
            -Dsonar.projectKey='$projectKey' \\
            -Dsonar.sources='${params.get('sources', './src')}' \\
            -Dsonar.exclusions='${params.get('exclusions', '')}'
          """
      }
    }
  }
}
