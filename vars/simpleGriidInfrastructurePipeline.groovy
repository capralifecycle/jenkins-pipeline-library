#!/usr/bin/groovy

/**
 * Pipeline that creates a zip of the yaml-files and includes a
 * config.json file as required by Griid.
 *
 * If the repo contains a Makefile, we will assume it contains
 * a validate target and will try to run this under Python 3.
 *
 * Parameters:
 *
 *  - artifactRoleArn (required): AWS role to assume to upload artifacts
 *  - artifactBucketName (required): Bucket name to upload to
 *  - artifactBucketKey (required): Bucket key to upload to
 *  - buildConfigParams: Parameters passed to buildConfig
 */
def call(Map args) {
  String artifactRoleArn = args["artifactRoleArn"]
    ?: { throw new RuntimeException("Missing arg: artifactRoleArn") }()

  String artifactBucketName = args["artifactBucketName"]
    ?: { throw new RuntimeException("Missing arg: artifactBucketName") }()

  String artifactBucketKey = args["artifactBucketKey"]
    ?: { throw new RuntimeException("Missing arg: artifactBucketKey") }()

  buildConfig(args.buildConfigParams ?: [:]) {
    dockerNode(label: "modern") {
      stage("Checkout source") {
        checkout scm
      }

      if (fileExists("Makefile")) {
        stage("Validate") {
          insideToolImage("python:3") {
            sh "make validate"
          }
        }
      }

      stage("Package files") {
        def utils = new no.capraconsulting.buildtools.Utils()
        def configJson = groovy.json.JsonOutput.toJson([
          Parameters: [
            S3Bucket: artifactBucketName,
            S3Key: artifactBucketKey,
            S3GitCommit: utils.getSourceGitCommit(),
            S3BuildNumber: env.BUILD_NUMBER,
            allowProduction: "allow",
          ],
        ])

        echo "Config: $configJson"
        writeFile(file: "config.json", text: configJson)

        sh "zip build.zip config.json *.yaml"
      }

      if (env.BRANCH_NAME == "master") {
        stage("Upload build") {
          withAwsRole(artifactRoleArn) {
            sh "aws cp build.zip s3://$artifactBucketName/$artifactBucketKey"
          }
        }
      }
    }
  }
}
