#!/usr/bin/groovy

def call(dockerImageName, suffix = null) {
  def lastImageId
  def cacheTag = dockerGetCacheTag(suffix)

  stage('Pull latest built image for possible cache') {
    sh "docker pull $dockerImageName:$cacheTag || docker pull $dockerImageName:latest || :"
    lastImageId = sh([
      returnStdout: true,
      script: "docker images -q $dockerImageName:$cacheTag"
    ]).trim()
    if (lastImageId == '') {
      lastImageId = sh([
        returnStdout: true,
        script: "docker images -q $dockerImageName:latest"
      ]).trim()
    }
  }

  return lastImageId
}
