#!/usr/bin/groovy

def call(builtImg, lastImageId) {
  def cacheTag = dockerGetCacheTag()

  def newImageId = sh([
    returnStdout: true,
    script: "docker images -q ${builtImg.id}"
  ]).trim()

  stage('Push Docker branch image for cache of next build') {
    if (newImageId == lastImageId) {
      echo 'We didn\'t bulid a new image - skipping'
    } else {
      builtImg.push(cacheTag)
    }
  }

  return newImageId == lastImageId
}
