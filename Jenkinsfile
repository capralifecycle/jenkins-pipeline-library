#!/usr/bin/env groovy

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

  def commitHash

  stage('Checkout source') {
    commitHash = checkout(scm).GIT_COMMIT
  }

  stage('Load current commit as library') {
    library "cals@$commitHash"
  }
}
