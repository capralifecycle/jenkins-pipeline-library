#!/usr/bin/groovy

/**
 * Add a configuration file for NPM providing authentication details to
 * the GitHub Packages NPM registry and npmjs. The location of the configuration file is stored in
 * the NPM_CONFIG_USERCONFIG variable which is automatically picked up by NPM.
 *
 * The config files are set up in Jenkins
 * by using config-file-provider plugin and uses credentials
 * from the credentials store in Jenkins.
 *
 * Example usage:
 *
 * withNpmConfig {
 *   stage("Install dependencies") {
 *     sh "npm ci"
 *   }
 * }
 */
def call(args = [:], body) {
  def credentialsId = args.credentialsId ?: 'github-packages-npm-read-settings'
  configFileProvider([
    configFile(fileId: credentialsId, variable: 'NPM_CONFIG_USERCONFIG'),
  ]) {
    body()
  }
}
