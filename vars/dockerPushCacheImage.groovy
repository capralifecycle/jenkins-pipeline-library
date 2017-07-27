#!/usr/bin/groovy

def call(dockerImageName) {
  def lastImageId
  def cacheTag = dockerGetCacheTag()

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
