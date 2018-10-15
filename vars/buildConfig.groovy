#!/usr/bin/groovy

/**
 * Generic configuration for jobs
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
    _slackNotifyBuild {
      body()
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

  // Notify Slack before and after we process the body
  try {
    slackNotifyBuild([ buildStatus: 'STARTED' ])
    body()
  } catch (e) {
    currentBuild.result = 'FAILURE'
    throw e
  } finally {
    slackNotifyBuild([ buildStatus: currentBuild.result ])
  }
}

