#!/usr/bin/groovy

/**
 * Provide neccessary configuration to give semantic-release
 * credentials to perform Git push, npmjs release, GitHub release and GitHub Packages release.
 *
 * For more details:
 * https://semantic-release.gitbook.io/semantic-release/usage/ci-configuration#authentication
 */
def call(body) {
  withCredentials([
    usernamePassword(
      credentialsId: 'github-calsci-token-with-user',
      passwordVariable: 'GH_TOKEN',
      usernameVariable: 'GH_TOKEN_USERNAME', // not used
    ),
  ]) {
    withEnv(['CI=true']) {
      /**
       * Configuration file with permission to release to GitHub Packages npm registry and npmjs
       */
      withNpmConfig([
        credentialsId: 'github-packages-npm-settings'
      ]){
        body()
      }
    }
  }
}
