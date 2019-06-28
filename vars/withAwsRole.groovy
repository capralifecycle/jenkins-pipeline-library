#!/usr/bin/groovy

/**
 * Assume a specific role in AWS using the current credentials.
 * By default the credentials of the Jenkins slave is used.
 *
 * Either run this outside any Docker container, or run it using
 * a Docker image that provides the aws cli and pass the
 * AWS_CONTAINER_CREDENTIALS_RELATIVE_URI environment
 * variable when running the container. E.g.:
 *
 *   myImage.inside('-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI') {
 *     withAwsRole('arn:....') {
 *       sh 'aws sts get-caller-identity'
 *     }
 *   }
 *
 * @param roleArn A full ARN of a role, e.g. arn:aws:iam::123456789123:role/some-role.
 * @param body Closure to be called
 */
def call(roleArn, body) {
  // Using 900 seconds as that is the minimum duration.
  credentials = sh(
    script: """
      aws sts assume-role \\
        --role-arn "$roleArn" \\
        --role-session-name jenkins-build \\
        --duration-seconds 900 \\
        --out text \\
        --query 'Credentials.[AccessKeyId, SecretAccessKey, SessionToken]'
    """,
    returnStdout: true
  ).trim().split("\t")

  // Avoiding using the AWS_ACCESS_KEY_ID etc. environment variables
  // as they will cause the values to be leaked in the logs if
  // environment variables gets printed by some part of the body.

  def tempCredentialFile = tempFileName()

  writeFile(
    file: tempCredentialFile,
    text:
      "[default]\n" +
      "aws_access_key_id=${credentials[0]}\n" +
      "aws_secret_access_key=${credentials[1]}\n" +
      "aws_session_token=${credentials[2]}\n"
  )

  try {
    withEnv([
      "AWS_SHARED_CREDENTIALS_FILE=$tempCredentialFile",
    ]) {
      body()
    }
  } finally {
    sh "rm -f '$tempCredentialFile'"
  }
}

def tempFileName() {
  pwd(tmp: true) + '/' + UUID.randomUUID().toString()
}
