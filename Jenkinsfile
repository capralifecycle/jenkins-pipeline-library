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

  stage('Load current commit as library') {
    library "cals@$commitHash"
  }
}
