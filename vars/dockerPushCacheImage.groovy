#!/usr/bin/groovy

def call(builtImg, lastImageId) {
  def cacheTag = dockerGetCacheTag()

  def newImageId = sh([
    returnStdout: true,
    script: "docker images -q ${builtImg.id}"
  ]).trim()

  stage('Push Docker branch image for cache of next build') {
    if (newImageId == lastImageId) {
      echo 'We didn\'t build a new image - skipping'
      echo 'History of existing image:'
    } else {
      builtImg.push(cacheTag)
      echo 'History of built image:'
    }

    sh "docker history ${builtImg.id}"
  }

  return newImageId == lastImageId
}
