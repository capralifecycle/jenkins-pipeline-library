#!/usr/bin/groovy

/**
 * Add settings.xml providing authentication information for our
 * mvn repository. The location of settings.xml is saved in
 * MAVEN_SETTINGS variable and can be used as such:
 *
 *   mvn -s $MAVEN_SETTINGS -B package
 *
 * The config file maven-settings-cals is set up in Jenkins
 * by using config-file-provider plugin and uses credentials
 * from the credentials store in Jenkins.
 */
def call(body) {
  configFileProvider([
    configFile(fileId: 'maven-settings-cals', variable: 'MAVEN_SETTINGS'),
  ]) {
    body()
  }
}
