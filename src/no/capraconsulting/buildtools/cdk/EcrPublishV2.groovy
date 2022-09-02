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
    roleArn = "arn:aws:iam::112233445566:role/build-role"
  }

  dockerNode {
    def tagName
    p.withEcrLogin(config) {
      def lastImageId = p.pullCache(config, "application-name")
      def img = docker.build(config.repositoryUri, "--cache-from $lastImageId --pull .")
      def isSameImage = p.pushCache("application-name", img, lastImageId)
      if (!isSameImage) {
        tagName = p.generateLongTag("application-name")
        img.push(tagName)
      }
    }
  }
*/

class ConfigDelegate implements Serializable {
  String repositoryUri
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
private def getCacheTags(applicationName) {
  def utils = new no.capraconsulting.buildtools.Utils()
  def tag = "ci-cache-$applicationName"

  if (env.BRANCH_NAME != null && env.BRANCH_NAME != "master") {
    // Tags based on branch names may occasionally exceed the allowed limit of 128 characters
    // The code below truncates the tag if necessary, and appends a short hash to keep the
    // tag unique for the given application and branch name
    def branch = utils.getSafeBranchName(env.BRANCH_NAME)
    def branchTag = tag + "-" + branch
    def maxTagLength = 128
    if (branchTag.length() > maxTagLength) {
      def suffix = "-" + org.apache.commons.codec.digest.DigestUtils.sha256Hex(branchTag).take(7)
      branchTag = branchTag.take(maxTagLength - suffix.length()) + suffix
    }
    return [
      branchTag,
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
def pullCache(ConfigDelegate config, String applicationName) {
  def lastImageId
  def cacheTags = getCacheTags(applicationName)

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
def pushCache(applicationName, builtImg, String lastImageId) {
  def cacheTag = getCacheTags(applicationName)[0] // Primary tag

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
def generateLongTag(String applicationName) {
  def utils = new no.capraconsulting.buildtools.Utils()
  return "${applicationName}-${utils.generateLongTag(new Date())}"
}


/**
 * Helper to reduce boilerplate in Jenkinsfile.
 *
 * Required arguments:
 *   - config: The ECR config.
 *   - applicationName: The application name being built.
 *
 * Special arguments that can be set:
 *   - contextDir: The Docker context dir to use.
 *   - dockerArgs: Additional arguments passed to docker build command.
 *
 * Returns a list of [img, isSameImageAsLast].
 */
def buildImage(Map args = [:]) {
  if (!args.containsKey("config")) {
    throw new Exception("Missing config as arg")
  }
  if (!args.containsKey("applicationName")) {
    throw new Exception("Missing applicationName as arg")
  }

  def img
  def lastImageId = pullCache(args.config, args.applicationName)
  def contextDir = args.contextDir ?: "."

  def dockerArgs = ""
  if (params.dockerSkipCache) {
    dockerArgs = " --no-cache"
  }
  if (args.dockerArgs != null) {
    dockerArgs = " ${args.dockerArgs}"
  }
  img = docker.build(
    "${args.config.repositoryUri}:${args.applicationName}",
    "--cache-from $lastImageId$dockerArgs $contextDir"
  )

  def isSameImageAsLast = pushCache(args.applicationName, img, lastImageId)
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
