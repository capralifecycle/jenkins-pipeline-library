#!/usr/bin/groovy

/**
 * Upload jar-artifact to S3 to the given
 * bucket name using the given IAM role.
 */
def call(Map config) {
  def artifactFileName = require(config, "artifactFileName")
  def artifactDir = require(config, "artifactDir")

  def bucketName = require(config, "artifactsBucketName")
  def bucketDir = require(config, "artifactsBucketDir")
  def roleArn = require(config, "artifactsRoleArn")

  dir(artifactDir) {
    def s3Key = "$bucketDir/$artifactFileName"
    def s3Url = "s3://$bucketName/$s3Key"

    withAwsRole(roleArn) {
      sh "aws s3 cp /$artifactDir/$artifactFileName $s3Url"
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
