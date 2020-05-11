#!/usr/bin/groovy
package no.capraconsulting.buildtools.cdk

/*
This file covers the deployment of new images for ECS services,
by using a pre-deployed lambda in the target account that
accepts the ECR tag to be deployed.

TODO: Add link to companion CDK library.

Ordering and concurrency is controlled to reduce possible issues
when having multiple builds queued.

Trust must already be established so that we can assume the
cross-account role.

Sketch of usage - leaving out details outside scope of this:

  def ecsUpdateImage = new EcsUpdateImage()
  def tag = "some-tag"

  stage("Deploy to staging") {
    ecsUpdateImage.deploy {
      milestone1 = 1
      milestone2 = 2
      lockName = "my-app-deploy-staging"
      tag = tag
      deployFunctionArn = "arn:aws:lambda:eu-west-1:112233445566:function:staging-deploy-fn"
      statusFunctionArn = "arn:aws:lambda:eu-west-1:112233445566:function:staging-status-fn"
      roleArn = "arn:aws:iam::112233445566:role/staging-deploy-role"
    }
  }
  stage("Run tests in staging") {
    ...
  }
  stage("Deploy to prod") {
    ecsUpdateImage.deploy {
      milestone1 = 3
      milestone2 = 4
      lockName = "my-app-deploy-prod"
      tag = tag
      deployFunctionArn = "arn:aws:lambda:eu-west-1:112233445588:function:prod-deploy-fn"
      statusFunctionArn = "arn:aws:lambda:eu-west-1:112233445588:function:prod-status-fn"
      roleArn = "arn:aws:iam::112233445588:role/prod-deploy-role"
    }
  }
*/

class DeployDelegate implements Serializable {
  int milestone1
  int milestone2
  String lockName
  String tag
  String deployFunctionArn
  String statusFunctionArn
  String roleArn
}

private def getFunctionRegion(String functionArn) {
  // Example value: arn:aws:lambda:eu-west-1:112233445566:function:my-function
  // Result: eu-west-1
  def result = (functionArn =~ "arn:aws:lambda:([^\\:]+):.+")
  if (!result.matches()) {
    throw new RuntimeException("Could not extract region from " + functionArn)
  }
  return result.group(1)
}

def deploy(Closure cl) {
  def config = new DeployDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def region = getFunctionRegion(config.deployFunctionArn)

  milestone config.milestone1

  // Get exclusive access and use LIFO instead of FIFO.
  lock(resource: config.lockName, inversePrecedence: true) {
    milestone config.milestone2

    // TODO: Consider releasing the node between sleeps or set up
    //  more lightweight executors for this use case.
    dockerNode {
      withAwsRole(config.roleArn) {
        timeout(time: 60) {
          sh """
            # Start the deployment.
            aws lambda invoke result \\
              --region $region \\
              --function-name ${config.deployFunctionArn} \\
              --payload '{
                "tag": "${config.tag}"
              }' >out

            cat result
            cat out

            which jq

            if ! jq -e ".FunctionError == null" out >/dev/null; then
              echo "Deploy failed - see logs"
              exit 1
            fi

            # Poll for completion.
            while true; do
              aws lambda invoke result \\
                --region $region \\
                --function-name ${config.statusFunctionArn} \\
                >out

              cat result
              cat out

              if ! jq -e ".FunctionError == null" out >/dev/null; then
                echo "Status check failed - see logs"
                exit 1
              fi

              # Verify the response actually contains expected data.
              if ! jq -e ".stabilized != null" result >/dev/null; then
                echo "Unexpected response - see logs"
                exit 1
              fi

              if jq -e ".stabilized" result >/dev/null; then
                echo "Deployment stabilized"
                break
              fi

              echo "Sleeping a bit before rechecking deployment status"
              sleep 10
            done

            # Verify expected tag, as we never want to continue in case
            # e.g. some manual override has happened.
            # If the current tag is null, we continue as that means we
            # are doing initial account/service deployment.
            if ! jq -e ".currentTag == \\\$tag" --arg tag "${config.tag}" result >/dev/null; then
              echo "Unexpected tag - aborting"
              exit 1
            fi
          """
        }
      }
    }
  }
}