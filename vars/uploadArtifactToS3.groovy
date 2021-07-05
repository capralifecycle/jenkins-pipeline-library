#!/usr/bin/groovy

/**
 * Upload jar-artifact to S3 to the given
 * bucket name using the given IAM role.
 */
def call(Map config) {
  def artifactPrefix = require(config, "artifactPrefix")
  def artifactDir = require(config, "artifactDir")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def artifactsRoleArn = require(config, "artifactsRoleArn")

  dir(artifactDir) {
    def fileName = sh """
      find . -type f -iname "$artifactPrefix*" -exec basename {} \\;
    """

    def fileExtension = sh """
      "\${$fileName##*.}"
    """

    // TODO: The file will include timestamps causing new hash to
    //  be created even when it has the same file contents.
    //  Consider reworking this so we can produce deterministic zip
    //  files for the same content excluding timestamps.

    def sha256 = sh([
      returnStdout: true,
      script: "sha256sum ./$fileName | awk '{print \$1}'"
    ]).trim()

    def s3Key = "builds/${sha256}.$fileExtension"
    def s3Url = "s3://$artifactsBucketName/$s3Key"

    withAwsRole(artifactsRoleArn) {
      sh "aws s3 cp /$artifactDir/$fileName $s3Url"
    }

    s3Key
  }
}

private def require(Map config, String name) {
  if (!config.containsKey(name)) {
    throw new Exception("Missing $name")
  }
  return config[name]
}
