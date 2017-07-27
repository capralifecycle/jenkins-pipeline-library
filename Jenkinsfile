#!/usr/bin/env groovy

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
