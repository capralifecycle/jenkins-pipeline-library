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
    body()
  }
}
