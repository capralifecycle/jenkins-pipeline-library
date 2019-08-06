#!/usr/bin/groovy

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
def call(Map<String, String> params, Map<String, String> options = [:]) {
  requireValue(params, 'sonar.projectKey')
  requireValue(params, 'sonar.organization')
  requireBinary('sonar-scanner')
  requireBinary('git')

  def defaultBranch = options.get('defaultBranch', 'master')
  def orgAndRepo = getGitHubOrganizationAndRepoName().join('/')

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
    withCredentials([
      string(
        credentialsId: 'sonarcloud-calsci-token',
        variable: 'SONARCLOUD_TOKEN',
      ),
    ]) {
      // Build base parameters. Provided parameters can override.
      Map<String, String> p = [:]

      p['sonar.host.url'] = 'https://sonarcloud.io'
      p['sonar.login'] = env.SONARCLOUD_TOKEN

      if (env.CHANGE_ID) {
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

def getGitHubOrganizationAndRepoName() {
  // Support both https and ssh formats.
  def parts = sh(returnStdout: true, script: 'git remote get-url origin')
    .trim()
    .split(':')[-1]
    .replaceAll(/\.git$/, '')
    .split('/')

  // Returns [orgName, repoName]
  return [parts[-2], parts[-1]]
}

def requireBinary(name) {
  def found = sh(returnStdout: true, script: "which $name >/dev/null && echo 1 || echo 0").trim()
  if (found != "1") {
    error("Binary `$name` was not found. You need to call analyzeSonarCloud inside an image that contains `$name`.")
  }
}

def requireValue(map, name) {
  if (!(name in map)) {
    error("$name must be set in parameter list to analyzeSonarCloud")
  }
}
