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
 * Get a safe string value of the branch name that can be
 * used in paths, artifact names etc.
 */
private def getSafeBranchName(branchName) {
  return branchName.replaceAll(/[^a-zA-Z0-9\\-]/, "-")
}

/**
 * Detect the actual commit that is used. Jenkins will create a
 * merge commit when doing pr-merge strategy, which we are not
 * interested in as it is only transient.
 */
private def getSourceGitCommitSha() {
  return sh(
    returnStdout: true,
    script: '''
      if git show -s --format=%s HEAD | grep -q '^Merge commit '; then
        git rev-parse HEAD~1
      else
        git rev-parse HEAD
      fi
    ''',
  ).trim()
}

/**
 * Get the branch name that Jenkins uses. For PR builds this
 * is not actual the Git branch name, but the job name used,
 * e.g. PR-1.
 *
 * TOOD: Consider retrieving the actual branch name for PR.
 */
private def getJenkinsBranchName() {
  // TODO: Check for null?
  return env.BRANCH_NAME
}

/**
 * Get list of tags used for cache.
 *
 * The first tag is the primary tag used, but other tags
 * will be read if primary is missing.
 */
private def getCacheTags(ConfigDelegate config) {
  def tag = "ci-cache-${config.applicationName}"

  if (env.BRANCH_NAME != null && env.BRANCH_NAME != "master") {
    return [
      tag + "-" + getSafeBranchName(env.BRANCH_NAME),
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
    script: "docker images -q ${builtImg.id}"
  ]).trim()

  echo "History of image:"
  sh "docker history ${builtImg.id}"

  def isSameImage = newImageId == lastImageId

  if (isSameImage) {
    echo "No new image built"
  } else {
    echo "New image seems to have been built"
    echo "Pushing Docker branch image for cache of next build using tag $cacheTag"
    builtImg.push(cacheTag)
  }

  return isSameImage
}

/**
 * Generate tag to be used for pushed image.
 */
def generateLongTag(ConfigDelegate config) {
  def now = sh([
    returnStdout: true,
    script: "date -u +%Y%m%d-%H%M%Sz"
  ]).trim()

  def branch = getSafeBranchName(getJenkinsBranchName())
  def build = env.BUILD_NUMBER
  def gitsha = getSourceGitCommitSha().take(7)

  return "${config.applicationName}-$now-$branch-$build-$gitsha"
}
