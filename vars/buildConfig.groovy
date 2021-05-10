#!/usr/bin/groovy

/**
 * Generic configuration for jobs.
 *
 * To make a job send a message to Slack on failures, set:
 *
 *   slack:
 *     channel: #channel
 *     teamDomain: liflig (optional, default to liflig)
 *     notifyAll: false (notify all branch builds) (optional, default to false)
 *     defaultBranch: master (branch on which to report failures) (optional, defaults to master)
 *
 * If a job is more complex and having multiple stages, consider using
 * the reportMasterFailures method instead.
 */
def call(Map parameters = [:], body) {
  // Persist parameters to be available in other library functions
  buildConfigParams(parameters)

  def projectProperties = []

  // TODO: parameter githubUrl is no longer used - signal to callers that it is ignored

  // Additional properties has to be given explicitly
  // because calling properties multiple times will cause
  // latest call to take precedence
  def jobProperties = parameters.get('jobProperties')
  if (jobProperties != null) {
    projectProperties = projectProperties + jobProperties
  }

  properties(projectProperties)

  // Make colors look good in Jenkins Console view
  ansiColor('xterm') {
    // Adds timestamps to the build log.
    timestamps {
      _slackNotifyBuild {
        // Set CI like what is used in Travis etc. This is used by lots of
        // tools to put special behaviour when running in CI.
        if (!parameters.get('skipCiEnv')) {
          withEnv(['CI=true']) {
            body()
          }
        } else {
          echo "DEPRECATED: Skipping CI=true due to skipCiEnv being set."
          body()
        }
      }
    }
  }
}

def _slackNotifyBuild(body) {
  def params = buildConfigParams().slack ?: [:]

  // Only notify Slack if we have specified at least a channel
  if (!params.channel) {
    body()
    return
  }

  // We default to only notify failures on the default branch
  // to produce less noise.
  def notifyAll = params.notifyAll ?: false
  def defaultBranch = params.defaultBranch ?: "master"

  catchError {
    if (notifyAll) {
      slackNotifyBuild([ buildStatus: 'STARTED' ])
    }
    body()
  }

  if (notifyAll || (env.BRANCH_NAME == defaultBranch && currentBuild.result == 'FAILURE')) {
    slackNotifyBuild([ buildStatus: currentBuild.result ])
  }
}
