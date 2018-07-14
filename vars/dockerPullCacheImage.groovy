#!/usr/bin/groovy

def call(dockerImageName, suffix = null) {
  def lastImageId
  def cacheTag = dockerGetCacheTag(suffix)

  echo 'Pulling latest built image for possible cache'
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

  if (lastImageId == '') {
    // We avoid returning blank to avoid trouble when passing the
    // --cache-from argument. Returning some other value will not change
    // the behaviour.
    lastImageId = 'NOCACHE'
  }

  echo "Cache image: $lastImageId"
  return lastImageId
}
