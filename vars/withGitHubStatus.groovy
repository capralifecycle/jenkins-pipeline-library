#!/usr/bin/groovy

/**
 * Set pending status to GitHub and success/failure when completing.
 *
 * Needs a node and the source code checked out in the project workspace.
 *
 * GitHub API must be set in Jenkins global configuration with a user that can
 * access the repository we are updating status of.
 */
def call(body) {
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
    // be filled out - default it to SUCCESS but restore value
    // afterwards.
    def previousResult = currentBuild.result
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

    currentBuild.result = previousResult
  }
}
