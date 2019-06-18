#!/usr/bin/groovy

/**
 * Provide neccessary configuration to give semantic-release
 * credentials to perform Git push, npmjs release and GitHub release.
 *
 * For more details:
 * https://semantic-release.gitbook.io/semantic-release/usage/ci-configuration#authentication
 */
def call(body) {
  withCredentials([
    string(
      credentialsId: 'npmjs-capraconsulting-deploy-token',
      variable: 'NPM_TOKEN',
    ),
    usernamePassword(
      credentialsId: 'github-calsci-token-with-user',
      passwordVariable: 'GH_TOKEN',
      usernameVariable: 'GH_TOKEN_USERNAME', // not used
    ),
  ]) {
    withEnv(['CI=true']) {
      body()
    }
  }
}
