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
      * Add a configuration file for NPM providing authentication details to
      * the GitHub Packages NPM registry and npmjs. The location of the configuration file is stored in
      * the NPM_CONFIG_USERCONFIG variable which is automatically picked up by NPM.
      *
      * The config file github-packages-npm-settings is set up in Jenkins
      * by using config-file-provider plugin and uses credentials
      * from the credentials store in Jenkins.
      */
      configFileProvider([
        configFile(fileId: 'github-packages-npm-settings', variable: 'NPM_CONFIG_USERCONFIG'),
      ]) {
        body()
      }
    }
  }
}
