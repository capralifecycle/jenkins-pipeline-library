#!/usr/bin/groovy
package no.capraconsulting.buildtools.sonarcloud

import groovy.json.JsonOutput

/**
 * Provide the environment variable SONARQUBE_SCANNER_PARAMS
 * that is picked up by the Sonar Scanner Maven plugin,
 * containing information about the current build, including
 * authentication to SonarCloud, branch details and pull
 * request details.
 *
 * See https://github.com/SonarSource/sonar-scanner-api/blob/49f8f2ade8c9f862a80480b7deca730d3e3ac8cf/api/src/main/java/org/sonarsource/scanner/api/Utils.java#L48
 */
def withEnvForMaven(body) {
  withSonarCloudCredentials {
    def json = JsonOutput.toJson(getBaseProperties())
    withEnv(["SONARQUBE_SCANNER_PARAMS=$json"]) {
      body()
    }
  }
}

/**
 * Analyze the project using SonarScanner and upload to SonarCloud.
 *
 * See also the following preferred high-level facades:
 *   - analyzeSonarCloudForMaven
 *   - analyzeSonarCloudForNodejs
 *
 * This must be called in a context that contains:
 *   - sonar-scanner
 *   - git
 *
 * See https://confluence.capraconsulting.no/x/dA9aBw for internal details.
 */
def runSonarScanner(Map<String, String> params, Map<String, String> options = [:]) {
  requireValue(params, 'sonar.projectKey')
  requireValue(params, 'sonar.organization')
  requireBinary('sonar-scanner')
  requireBinary('git')

  def defaultBranch = options.get('defaultBranch', 'master')

  // SonarScanner wants target branch to be fetched, or else we will get
  // "WARN: Could not find ref: master in refs/heads or refs/remotes/origin"
  def target = env.CHANGE_TARGET ? env.CHANGE_TARGET : defaultBranch
  if (env.BRANCH_NAME != target) {
    withGitTokenCredentials {
      sh """
        if ! git rev-parse --verify origin/$target; then
          git fetch --no-tags origin +refs/heads/$target:refs/remotes/origin/$target
        fi
      """
    }
  }

  stage('SonarScanner with SonarCloud') {
    withSonarCloudCredentials {
      def p = getBaseProperties()

      def finalParams = (p + params)
        .collect { k, v ->
          "$k=$v"
        }
        .join("\n")

      // Write properties to file instead of trying to pass them as parameters.
      def propertiesFile = pwd(tmp: true) + '/sonar-project.properties'
      writeFile(
        file: propertiesFile,
        text: finalParams,
        encoding: 'utf-8',
      )

      // The token will be masked by the credentials plugin.
      echo "Executing sonar-scanner with these parameters:\n$finalParams"

      sh "sonar-scanner -Dproject.settings='${propertiesFile}'"
    }
  }
}

private def withSonarCloudCredentials(body) {
  withCredentials([
    string(
      credentialsId: 'sonarcloud-calsci-token',
      variable: 'SONARCLOUD_TOKEN',
    ),
  ]) {
    body()
  }
}

private def getBaseProperties() {
  // Build base parameters. Provided parameters can override.
  Map<String, String> p = [:]

  p['sonar.host.url'] = 'https://sonarcloud.io'
  p['sonar.login'] = env.SONARCLOUD_TOKEN

  if (env.CHANGE_ID) {
    def orgAndRepo = getGitHubOrganizationAndRepoName().join('/')

    // https://sonarcloud.io/documentation/analysis/pull-request/
    p['sonar.pullrequest.base'] = env.CHANGE_TARGET
    p['sonar.pullrequest.branch'] = env.BRANCH_NAME
    p['sonar.pullrequest.key'] = env.CHANGE_ID
    p['sonar.pullrequest.provider'] = 'GitHub'
    p['sonar.pullrequest.github.repository'] = orgAndRepo

    // Detect the actual commit that is used. Jenkins will create a
    // merge commit when doing pr-merge strategy which SonarCloud will
    // never know about.
    p['sonar.scm.revision'] = sh(
      returnStdout: true,
      script: '''
        if git show -s --format=%s HEAD | grep -q '^Merge commit '; then
          git rev-parse HEAD~1
        else
          git rev-parse HEAD
        fi
      ''',
    ).trim()
  } else {
    // https://sonarcloud.io/documentation/branches/overview/
    p['sonar.branch.name'] = env.BRANCH_NAME
  }

  return p
}

private def getGitHubOrganizationAndRepoName() {
  // Support both https and ssh formats.
  def parts = sh(returnStdout: true, script: 'git remote get-url origin')
    .trim()
    .split(':')[-1]
    .replaceAll(/\.git$/, '')
    .split('/')

  // Returns [orgName, repoName]
  return [parts[-2], parts[-1]]
}

private def requireBinary(name) {
  def found = sh(returnStdout: true, script: "which $name >/dev/null && echo 1 || echo 0").trim()
  if (found != "1") {
    error("Binary `$name` was not found. You need to call analyzeSonarCloud inside an image that contains `$name`.")
  }
}

private def requireValue(map, name) {
  if (!(name in map)) {
    error("$name must be set in parameter list to analyzeSonarCloud")
  }
}
