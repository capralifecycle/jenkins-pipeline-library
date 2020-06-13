#!/usr/bin/groovy
package no.capraconsulting.buildtools.cdk

/*
This file covers the deployment of CloudFormation stacks
built by CDK using a Cloud Assembly. It is performed by
pre-provisioned resources in the target account, and uses
a reference to a ZIP containing the Cloud Assembly uploaded
by a previous stage.

This is a companion to the setup found in
https://github.com/capralifecycle/liflig-cdk/tree/master/src/cdk-deploy

Ordering and concurrency is controlled to reduce possible issues
when having multiple builds queued.

Trust must already be established so that we can assume the
cross-account role.

Sketch of usage - leaving out details outside scope of this:

  def cdkDeploy = new CdkDeploy()
  def deploy = cdkDeploy.createDeployFn {
    cloudAssemblyS3Url = "s3://my-bucket/414fb2ca392.zip"
    deployFunctionArn = "arn:aws:lambda:eu-west-1:112233445566:function:staging-deploy-fn"
    statusFunctionArn = "arn:aws:lambda:eu-west-1:112233445566:function:staging-status-fn"
    roleArn = "arn:aws:iam::112233445566:role/staging-deploy-role"
  }
  stage("Deploy to staging") {
    cdkDeploy.withLock({
      milestone1 = 1
      milestone2 = 2
      lockName = "my-app-deploy-staging"
    }) {
      deploy([
        "staging-stack-a",
        "staging-stack-b",
      ])
    }
  }
  stage("Run tests in staging") {
    ...
  }
  stage("Deploy to prod") {
    forArtifact.withLock({
      milestone1 = 3
      milestone2 = 4
      lockName = "my-app-deploy-prod"
      deployFunctionArn = "arn:aws:lambda:eu-west-1:112233445588:function:prod-deploy-fn"
      statusFunctionArn = "arn:aws:lambda:eu-west-1:112233445588:function:prod-status-fn"
      roleArn = "arn:aws:iam::112233445588:role/prod-deploy-role"
    }) { deploy ->
      deploy([
        "prod-stack-a",
        "prod-stack-b",
      ])
    }
  }
*/

class CreateDeployFnDelegate implements Serializable {
  /** @deprecated see cloudAssemblyS3Url */
  String bucketName
  /** @deprecated see cloudAssemblyS3Url */
  String bucketKey
  String cloudAssemblyS3Url
  String roleArn
  String deployFunctionArn
  String statusFunctionArn
}

class WithLockDelegate implements Serializable {
  int milestone1
  int milestone2
  String lockName
}

class CreateAndUploadCloudAssemblyDelegate implements Serializable {
  String roleArn
  String bucketName
}

def createDeployFn(Closure cl) {
  def config = new CreateDeployFnDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def cloudAssemblyS3Url = config.cloudAssemblyS3Url
    ?: "s3://${config.bucketName}/${config.bucketKey}"

  return { stacks ->
    _deploy(
      cloudAssemblyS3Url,
      config.roleArn,
      config.deployFunctionArn,
      config.statusFunctionArn,
      stacks
    )
  }
}

def withLock(Closure cl, Closure body) {
  def config = new WithLockDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  milestone config.milestone1

  // Get exclusive access and use LIFO instead of FIFO.
  lock(resource: config.lockName, inversePrecedence: true) {
    milestone config.milestone2
    body()
  }
}

// Internal method.
private def _deploy(
  cloudAssemblyS3Url,
  roleArn,
  deployFunctionArn,
  statusFunctionArn,
  stackNames
) {
  def (bucketName, bucketKey) = Util._decomposeBucketUrl(cloudAssemblyS3Url)
  def region = Util._getFunctionRegion(deployFunctionArn)

  // TODO: Consider releasing the node between sleeps or set up
  //  more lightweight executors for this use case.
  dockerNode {
    withAwsRole(roleArn) {
      timeout(time: 60) {
        sh """
          # Reduce noise in build log.
          set +x

          echo "Deploying stacks: ${stackNames.join(", ")}"

          # Start the deployment.
          payload='{
            "bucketName": "$bucketName",
            "bucketKey": "$bucketKey",
            "stackNames": [${stackNames.collect { "\"$it\"" }.join(", ")}]
          }'
          echo "Sending payload: \$payload"
          aws lambda invoke result \\
            --region $region \\
            --function-name $deployFunctionArn \\
            --payload "\$payload" \\
            >out

          if jq -e ".FunctionError != null" out >/dev/null; then
            echo "Lambda outfile":
            cat out
            echo "Response:"
            cat result
            echo "Deploy failed - see logs"
            exit 1
          fi

          # Extract job ID used for polling.
          if jq -e ".jobId == null" result >/dev/null; then
            echo "jobId not found in response"
            exit 1
          fi
          payload=\$(jq "{jobId: .jobId}" result)
          echo "Payload for status check: \$payload"

          # Poll for completion.
          while true; do
            aws lambda invoke result \\
              --region $region \\
              --function-name ${statusFunctionArn} \\
              --payload "\$payload" \\
              >out

            if jq -e ".FunctionError != null" out >/dev/null; then
              echo "Lambda outfile":
              cat out
              echo "Response:"
              cat result
              echo "Status check failed - see logs"
              exit 1
            fi

            if jq -e ".logs != null" result >/dev/null; then
              echo "Logs from deployment:"
              jq -r .logs result
            fi

            if jq -e ".success == true" result >/dev/null; then
              echo "Deploy completed"
              break
            elif jq -e ".success == false" result >/dev/null; then
              echo "Deploy failed"
              exit 1
            fi

            echo "Sleeping a bit before rechecking deployment status"
            sleep 10
          done
        """
      }
    }
  }
}

/**
 * Run cdk synth to produce a Cloud Assembly and upload this to S3.
 *
 * Returns the S3 url for the uploaded Cloud Assembly zip file.
 */
def createAndUploadCloudAssembly(Closure cl) {
  def config = new CreateAndUploadCloudAssemblyDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  sh """
    rm -rf cdk.out
    npm run cdk -- synth >/dev/null
    cd cdk.out
    zip -r ../cloud-assembly.zip .
  """

  def sha256 = sh([
    returnStdout: true,
    script: "sha256sum cloud-assembly.zip | awk '{print \$1}'"
  ]).trim()

  def s3Url = "s3://${config.bucketName}/cloud-assembly/${sha256}.zip"

  withAwsRole(config.roleArn) {
    sh "aws s3 cp cloud-assembly.zip $s3Url"
  }

  return s3Url
}
