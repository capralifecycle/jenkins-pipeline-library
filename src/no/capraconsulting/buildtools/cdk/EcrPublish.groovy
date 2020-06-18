#!/usr/bin/groovy
package no.capraconsulting.buildtools.cdk

/*
This file covers usage of ECR repositories shared across multiple
applications, where the ECR repository is located in another account.

Trust must already be established so that we can assume the
cross-account role.

Sketch of usage - leaving out details outside scope of this:

  def p = new EcrPublish()
  def config = p.config {
    repositoryUri = "112233445566.dkr.ecr.eu-west-1.amazonaws.com/some-repo"
    applicationName = "some-app-name"
    roleArn = "arn:aws:iam::112233445566:role/build-role"
  }

  dockerNode {
    def tagName
    p.withEcrLogin(config) {
      def lastImageId = p.pullCache(config)
      def img = docker.build(config.repositoryUri, "--cache-from $lastImageId --pull .")
      def isSameImage = p.pushCache(config, img, lastImageId)
      if (!isSameImage) {
        tagName = p.generateLongTag(config)
        img.push(tagName)
      }
    }
  }
*/

class ConfigDelegate implements Serializable {
  String repositoryUri
  String applicationName
  /** Optional. Needed for withEcrLogin. */
  String roleArn
}

def config(Closure cl) {
  def config = new ConfigDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()
  return config
}

private def getRepositoryRegion(ConfigDelegate config) {
  // Example value: 112233445566.dkr.ecr.eu-west-1.amazonaws.com/some-repo
  // Result: eu-west-1
  def result = (config.repositoryUri =~ ".*\\.([^\\.]+)\\.amazonaws.com/.+")
  if (!result.matches()) {
    throw new RuntimeException("Could not extract region from " + config.repositoryUri)
  }
  return result.group(1)
}

private def getRepositoryServer(ConfigDelegate config) {
  // Example value: 112233445566.dkr.ecr.eu-west-1.amazonaws.com/some-repo
  // Result: 112233445566.dkr.ecr.eu-west-1.amazonaws.com
  def result = (config.repositoryUri =~ "([^/]+)+/.+")
  if (!result.matches()) {
    throw new RuntimeException("Could not extract server from " + config.repositoryUri)
  }
  return result.group(1)
}

/**
 * Get list of tags used for cache.
 *
 * The first tag is the primary tag used, but other tags
 * will be read if primary is missing.
 */
private def getCacheTags(ConfigDelegate config) {
  def utils = new no.capraconsulting.buildtools.Utils()
  def tag = "ci-cache-${config.applicationName}"

  if (env.BRANCH_NAME != null && env.BRANCH_NAME != "master") {
    return [
      tag + "-" + utils.getSafeBranchName(env.BRANCH_NAME),
      tag
    ]
  }

  return [tag]
}

/**
 * Run a block while being logged in to ECR server so that we can
 * pull and push images.
 */
def withEcrLogin(ConfigDelegate config, Closure body) {
  try {
    withAwsRole(config.roleArn) {
      echo "Logging into ${getRepositoryServer(config)}"
      sh "(set +x; eval \$(aws ecr get-login --no-include-email --region ${getRepositoryRegion(config)}))"
    }
    body()
  } finally {
    echo "Removing credentials for ECR"
    sh "docker logout ${getRepositoryServer(config)}"
  }
}

/**
 * Try to pull cache and return the image ID if found and pulled.
 */
def pullCache(ConfigDelegate config) {
  def lastImageId
  def cacheTags = getCacheTags(config)

  echo "Trying to pull possible cache image"
  for (int i = 0; i < cacheTags.size(); i++) {
    def cacheTag = cacheTags[i]
    sh "docker pull ${config.repositoryUri}:$cacheTag || :"

    lastImageId = sh([
      returnStdout: true,
      script: "docker images -q ${config.repositoryUri}:$cacheTag"
    ]).trim()

    if (lastImageId != "") {
      break
    }
  }

  if (lastImageId == "") {
    // We avoid returning blank to avoid trouble when passing the
    // --cache-from argument. Returning some other value will not change
    // the behaviour.
    lastImageId = "NOCACHE"
  }

  echo "Cache image: $lastImageId"
  return lastImageId
}

/**
 * Push the specified image as cache for next run. Returns a
 * boolean if the built image is the same as previously.
 */
def pushCache(ConfigDelegate config, builtImg, String lastImageId) {
  def cacheTag = getCacheTags(config)[0] // Primary tag

  def newImageId = sh([
    returnStdout: true,
    script: "docker images -q ${builtImg.id} | head -1"
  ]).trim()

  echo "History of image:"
  sh "docker history ${builtImg.id}"

  def isSameImage = newImageId == lastImageId

  if (isSameImage) {
    echo "No new image built"
  } else {
    echo "New image seems to have been built"
    echo "Last image ID: $lastImageId"
    echo "New image ID: $newImageId"
    echo "Pushing Docker branch image for cache of next build using tag $cacheTag"
    builtImg.push(cacheTag)
  }

  return isSameImage
}

/**
 * Generate tag to be used for pushed image.
 */
def generateLongTag(ConfigDelegate config) {
  def utils = new no.capraconsulting.buildtools.Utils()
  return "${config.applicationName}-${utils.generateLongTag(new Date())}"
}


/**
 * Helper to reduce boilerplate in Jenkinsfile.
 *
 * Special arguments that can be set:
 *   - contextDir: The Docker context dir to use.
 *   - dockerArgs: Additional arguments passed to docker build command.
 *
 * Returns a list of [img, isSameImageAsLast].
 */
def buildImage(ConfigDelegate config, Map args = [:]) {
  def img
  def lastImageId = pullCache(config)
  def contextDir = args.contextDir ?: "."

  stage("Build Docker image") {
    def dockerArgs = ""
    if (params.dockerSkipCache) {
      dockerArgs = " --no-cache"
    }
    if (args.dockerArgs != null) {
      dockerArgs = " ${args.dockerArgs}"
    }
    img = docker.build(config.repositoryUri, "--cache-from $lastImageId$dockerArgs --pull $contextDir")
  }

  def isSameImageAsLast = pushCache(config, img, lastImageId)
  return [img, isSameImageAsLast]
}

/**
 * Helper for setting dockerSkipCache param.
 */
def dockerSkipCacheParam() {
  return booleanParam(
    defaultValue: false,
    description: "Force build without Docker cache",
    name: "dockerSkipCache"
  )
}
