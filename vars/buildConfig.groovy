#!/usr/bin/groovy

/**
 * Generic configuration for jobs
 */
def call(Map parameters = [:], body) {
  def projectProperties = []

  def githubUrl = parameters.get('githubUrl')
  if (githubUrl != null) {
    projectProperties = projectProperties + [
      pipelineTriggers([
        // Build when pushing to repo
        githubPush(),
      ]),

      // "GitHub project"
      [
        $class: 'GithubProjectProperty',
        displayName: '',
        projectUrlStr: githubUrl
      ],
    ]
  }

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
    _slackNotifyBuild(parameters.slack) {
      body()
    }
  }
}

def _slackNotifyBuild(Map params = [:], body) {
  // Only notify Slack if we have specified at least a channel
  if (!params.channel) {
    body()
    return
  }

  // Notify Slack before and after we process the body
  try {
    slackNotifyBuild(params + [ buildStatus: 'STARTED' ])
    body()
  } catch (e) {
    currentBuild.result = 'FAILURE'
    throw e
  } finally {
    slackNotifyBuild(params)
  }
}

