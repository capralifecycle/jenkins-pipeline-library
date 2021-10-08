#!/usr/bin/groovy

/**
 * Upload an artifact (either a file or a directory) to the given S3
 * bucket using the given IAM role.
 * If it's a file, the file will be uploaded with a sanitized filename prefixed with the SHA256-hash of the file, with the original file extension preserved (e.g., `builds/<sha256>-my-file.jar`)
 * If it's a directory, the uploaded artifact will be a single ZIP file (e.g., `builds/<sha256>.zip`) containing the contents of the directory.
 */
def call(Map config) {
  def artifact = require(config, "artifact")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def artifactsRoleArn = require(config, "artifactsRoleArn")
  def isDir = sh([
    returnStdout: true,
    script: "if test -d \"${artifact}\"; then echo 'true'; else echo 'false'; fi"
  ]).trim()
  if (isDir == "true") {
    // If it's a directory, ZIP it and upload
    uploadArtifactDirAsZip(
      artifactDir: artifact,
      artifactsRoleArn: artifactsRoleArn,
      artifactsBucketName: artifactsBucketName,
    )
  } else {
    // If it's a single file, sanitize the filename and upload
    def fileName = artifact.split("/").last()
    def sanitized = fileName.replaceAll("[^a-zA-Z0-9._-]+", "")
    def sha256 = sh([
      returnStdout: true,
      script: "sha256sum \"${artifact}\" | awk '{print \$1}'"
    ]).trim()
    def s3Key = "builds/${sha256}-${sanitized}"
    def s3Url = "s3://$artifactsBucketName/$s3Key"
    withAwsRole(artifactsRoleArn) {
      sh "aws s3 cp \"${artifact}\" $s3Url"
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
