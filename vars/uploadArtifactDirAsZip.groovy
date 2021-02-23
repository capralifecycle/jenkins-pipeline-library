#!/usr/bin/groovy

/**
 * Zip a directory and upload it to S3 to the given
 * bucket name using the given IAM role.
 */
def call(Map config) {
  def artifactDir = require(config, "artifactDir")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def artifactsRoleArn = require(config, "artifactsRoleArn")

  dir(artifactDir) {
    sh """
      rm -f /tmp/artifact.zip
      zip -r /tmp/artifact.zip .
    """
  }

  // TODO: The zip file will include timestamps causing new hash to
  //  be created even when it has the same file contents.
  //  Consider reworking this so we can produce deterministic zip
  //  files for the same content excluding timestamps.

  def sha256 = sh([
    returnStdout: true,
    script: "sha256sum /tmp/artifact.zip | awk '{print \$1}'"
  ]).trim()

  def s3Key = "${sha256}.zip"
  def s3Url = "s3://$artifactsBucketName/$s3Key"

  withAwsRole(artifactsRoleArn) {
    sh "aws s3 cp /tmp/artifact.zip $s3Url"
  }

  sh "rm /tmp/artifact.zip"

  s3Key
}

private def require(Map config, String name) {
  if (!config.containsKey(name)) {
    throw new Exception("Missing $name")
  }
  return config[name]
}
