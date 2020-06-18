#!/usr/bin/groovy
package no.capraconsulting.buildtools.cdk

class PublishDelegate implements Serializable {
  String name
  String roleArn
  String bucketName
  /** The directory that contains the build that should be packaged. */
  String buildDir = "build"
}

/**
 * Package the built artifacts and upload to the artifacts repo.
 *
 * Return the S3 URL for the artifact.
 */
def publish(Closure cl) {
  def config = new PublishDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def utils = new no.capraconsulting.buildtools.Utils()

  withAwsRole(config.roleArn) {
    def now = new Date()
    def base = utils.generateLongTag(now)
    def yearMonth = now.format("yyyy-MM", TimeZone.getTimeZone("UTC"))

    def s3Url = "s3://${config.bucketName}/webapp/${config.name}/$yearMonth/${base}.tgz"

    sh "tar zcf build.tgz -C ${config.buildDir} ."
    sh "aws s3 cp build.tgz $s3Url"

    s3Url
  }
}

class DeployDelegate implements Serializable {
  String artifactS3Url
  String roleArn
  String functionArn
}

/**
 * Deploy a published artifact to an environment for a setup
 * using https://github.com/capraconsulting/webapp-deploy-lambda.
 */
def deploy(Closure cl) {
  def config = new DeployDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def region = Util._getFunctionRegion(config.functionArn)

  withAwsRole(config.roleArn) {
    sh """
      aws lambda invoke result \\
        --region $region \\
        --function-name ${config.functionArn} \\
        --payload '{
          "artifactS3Url": "${config.artifactS3Url}"
        }' \\
        >out

      echo "Lambda outfile":
      cat out
      echo "Response:"
      cat result

      if jq -e ".FunctionError != null" out >/dev/null; then
        echo "Deploy failed - see logs"
        exit 1
      fi
    """
  }
}
