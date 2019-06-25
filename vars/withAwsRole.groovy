#!/usr/bin/groovy

/**
 * Assume a specific role in AWS using the current credentials.
 * By default the credentials of the Jenkins slave is used.
 *
 * NOTE: CODE RUNNING IN THIS BLOCK MUST NOT PRINT ITS
 * ENVIRONMENT VARIABLES, AS IT WILL LEAK THE TEMPORARY
 * CREDENTIALS IN THE LOGS. AVOID WRAPPING YOUR WHOLE JOB
 * WITH THIS.
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
 * TODO: Mask credentials when a plugin supports it,
 * e.g. https://issues.jenkins-ci.org/browse/JENKINS-48740
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

  withEnv([
    "AWS_ACCESS_KEY_ID=${credentials[0]}",
    "AWS_SECRET_ACCESS_KEY=${credentials[1]}",
    "AWS_SESSION_TOKEN=${credentials[2]}",
  ]) {
    body()
  }
}
