#!/usr/bin/groovy

def call(builtImg, lastImageId, suffix = null) {
  def cacheTag = dockerGetCacheTag(suffix)

  def newImageId = sh([
    returnStdout: true,
    script: "docker images -q ${builtImg.id}"
  ]).trim()

  echo "Pushing Docker branch image for cache of next build using tag $cacheTag"

  if (newImageId == lastImageId) {
    echo 'We didn\'t build a new image - skipping'
    echo 'History of existing image:'
  } else {
    builtImg.push(cacheTag)
    echo 'History of built image:'
  }

  sh "docker history ${builtImg.id}"
  return newImageId == lastImageId
}
