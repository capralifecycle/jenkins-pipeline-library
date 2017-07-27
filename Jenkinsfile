#!/usr/bin/env groovy

// We avoid using our library in this test to avoid
// causing an evil loop difficult to recover

properties([
  pipelineTriggers([
    // Build when pushing to repo
    githubPush(),
  ]),

  // "GitHub project"
  [
    $class: 'GithubProjectProperty',
    displayName: '',
    projectUrlStr: 'https://github.com/capralifecycle/jenkins-pipeline-library/'
  ],
])

node('docker') {
  deleteDir()
  def commitHash = checkout(scm).GIT_COMMIT

  withGitHubStatus {
    stage('Load current commit as library') {
      library "cals@$commitHash"
    }
  }
}

// Copy of vars/withGitHubStatus due to not including the library
def withGitHubStatus(body) {
  try {
    // set GitHub build status "in-progress"
    step([
      $class: 'GitHubSetCommitStatusBuilder',
      statusMessage: [content: 'Building on Jenkins'],
      contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'ci/jenkins.capra.tv']
    ])

    body()
  } catch (e) {
    currentBuild.result = 'FAILURE'
    throw e
  } finally {
    // The GitHub plugin uses currentBuild.result which might not
    // be filled out - default it to SUCCESS.
    if (currentBuild.result == null) {
      currentBuild.result = 'SUCCESS'
    }

    step([
      $class: 'GitHubCommitStatusSetter',
      contextSource: [$class: 'ManuallyEnteredCommitContextSource', context: 'ci/jenkins.capra.tv'],
      errorHandlers: [[$class: 'ShallowAnyErrorHandler']],
      statusResultSource: [
        $class: 'ConditionalStatusResultSource',
        results: [
          [$class: 'BetterThanOrEqualBuildResult', result: 'SUCCESS', state: 'SUCCESS', message: 'Success build in Jenkins'],
          [$class: 'BetterThanOrEqualBuildResult', result: 'FAILURE', state: 'FAILURE', message: 'Failed build in Jenkins'],
          [$class: 'AnyBuildResult', state: 'FAILURE', message: 'Loophole']
        ]
      ]
    ])
  }
}
