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

  try {
    slackIt('STARTED')
    stage('Load current commit as library') {
      library "cals@$commitHash"
    }
  } catch (e) {
    currentBuild.result = 'FAILURE'
    throw e
  } finally {
    slackIt('SUCCESS')
  }
}

def slackIt(buildStatusDefault) {
  def buildStatus = currentBuild.result ?: buildStatusDefault

  def color
  if (buildStatus == 'STARTED') {
    color = ''
  } else if (buildStatus == 'SUCCESS') {
    color = 'good'
  } else {
    color = 'danger'
  }

  slackSend([
    channel: '#cals-dev-info',
    color: color,
    message: "${buildStatus}: Job `<${env.BUILD_URL}|${env.JOB_NAME} [${env.BUILD_NUMBER}]>`",
    teamDomain: 'cals-capra',
    tokenCredentialId: 'slack-cals-webhook-token',
  ])
}
