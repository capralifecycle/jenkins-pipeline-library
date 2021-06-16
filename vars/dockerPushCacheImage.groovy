#!/usr/bin/groovy

def call(builtImg, lastImageId, suffix = null) {
  def cacheTag = dockerGetCacheTag(suffix)

  // Ensure we point to a tag so that we only match one specific image.
  def imageRef = builtImg.id
  if (!imageRef.contains(":")) {
    imageRef += ":latest"
  }

  def newImageId = sh([
    returnStdout: true,
    script: "docker images -q $imageRef"
  ]).trim()

  echo "Pushing Docker branch image for cache of next build using tag $cacheTag"
  echo "Comparing new image ID ($newImageId) against last known image ID ($lastImageId)"

  if (newImageId == lastImageId) {
    echo 'We didn\'t build a new image - skipping'
    echo 'History of existing image:'
  } else {
    builtImg.push(cacheTag)
    echo 'History of built image:'
  }

  sh "docker history $imageRef"
  return newImageId == lastImageId
}
