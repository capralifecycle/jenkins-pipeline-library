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

    dockerNode {
      withAwsRole(config.roleArn) {
        // TODO: Consider switching to polling instead of blocking.
        timeout(time: 15) {
          sh """
            aws lambda invoke out \\
              --region $region \\
              --function-name ${config.deployFunctionArn} \\
              --cli-read-timeout 0 \\
              --payload '{
                "tag": "${config.tag}"
              }'
            cat out
          """
        }

        def result = readJSON(file: "out", returnPojo: true)
        if (result.containsKey("FunctionError")) {
          error("Deploy failed - see logs")
        }
      }
    }
  }
}
