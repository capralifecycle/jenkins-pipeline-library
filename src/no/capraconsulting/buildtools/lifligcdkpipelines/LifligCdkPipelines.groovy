#!/usr/bin/groovy
package no.capraconsulting.buildtools.lifligcdkpipelines

// This is companion to https://github.com/capralifecycle/liflig-cdk/tree/master/src/pipelines

// This is not to be confused with "CDK Pipelines" provided by the AWS CDK library,
// but covers Liflig's implementation of pipelines for CDK.

/**
 * Package the source files representing the CDK application
 * and upload it to S3.
 *
 * Returns the bucket key for the uploaded Cloud Assembly zip file.
 */
def packageAndUploadCdkSource(Map config) {
  def bucketName = require(config, "bucketName")
  def roleArn = require(config, "roleArn")
  def include = require(config, "include")

  sh """
    rm -f cdk-source.zip
    zip -r cdk-source.zip $include
  """

  def sha256 = sh([
    returnStdout: true,
    script: "sha256sum cdk-source.zip | awk '{print \$1}'"
  ]).trim()

  def s3Key = "cdk-source/${sha256}.zip"
  def s3Url = "s3://$bucketName/$s3Key"

  withAwsRole(roleArn) {
    sh "aws s3 cp cdk-source.zip $s3Url"
  }

  return s3Key
}

/**
 * Run cdk synth to produce a Cloud Assembly and upload this to S3.
 *
 * Returns the bucket key for the uploaded Cloud Assembly zip file.
 */
def createAndUploadCloudAssembly(Map config) {
  def bucketName = require(config, "bucketName")
  def roleArn = require(config, "roleArn")

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

  def s3Key = "cloud-assembly/${sha256}.zip"
  def s3Url = "s3://$bucketName/$s3Key"

  withAwsRole(roleArn) {
    sh "aws s3 cp cloud-assembly.zip $s3Url"
  }

  return s3Key
}

/**
 * Configure Liflig CDK pipelines using a uploaded CDK source.
 * Then trigger the pipeline.
 */
def configureAndTriggerCdkSourcePipelines(Map config) {
  def cdkSourceBucketKey = require(config, "cdkSourceBucketKey")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def artifactsRoleArn = require(config, "artifactsRoleArn")
  def pipelines = require(config, "pipelines")

  withAwsRole(artifactsRoleArn) {
    for (def pipelineName : pipelines) {
      configureCdkSource(
        cdkSourceBucketKey: cdkSourceBucketKey,
        artifactsBucketName: artifactsBucketName,
        pipelineName: pipelineName,
      )

      triggerPipeline(
        artifactsBucketName: artifactsBucketName,
        pipelineName: pipelineName,
      )
    }
  }
}

/**
 * Configure Liflig CDK pipelines using the uploaded CloudAssembly
 * and a list of environments and stacks the pipeline should cover.
 * Then trigger the pipeline.
 *
 * This is to be used with the legacy pipeline using Step Functions.
 *
 * The list of pipelines should be structured as:
 *
 *   [
 *     "name-of-pipeline": [
 *       "environments": [
 *         "name-of-environment": [
 *           "list",
 *           "of",
 *           "cdk",
 *           "stacks",
 *         ],
 *       ],
 *       "parameters": [    // optional
 *         "stack-1:ParameterName": "VariableName",
 *       ],
 *     ],
 *   ]
 */
def configureAndTriggerPipelines(Map config) {
  def cloudAssemblyBucketKey = require(config, "cloudAssemblyBucketKey")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def artifactsRoleArn = require(config, "artifactsRoleArn")
  def pipelines = require(config, "pipelines")

  withAwsRole(artifactsRoleArn) {
    for (def pipelineName : pipelines.keySet()) {
      configureCloudAssembly(
        cloudAssemblyBucketKey: cloudAssemblyBucketKey,
        artifactsBucketName: artifactsBucketName,
        pipelineName: pipelineName,
        parameters: pipelines[pipelineName]["parameters"] ?: [:],
        environments: pipelines[pipelineName]["environments"],
      )

      triggerPipeline(
        artifactsBucketName: artifactsBucketName,
        pipelineName: pipelineName,
      )
    }
  }
}

/**
 * Configure Liflig CDK pipelines using the uploaded CloudAssembly.
 * Then trigger the pipeline.
 *
 * This is to be used with the setup based on CDK Pipelines.
 *
 * The list of pipelines is a string list of pipeline names.
 */
def configureAndTriggerPipelinesV2(Map config) {
  def cloudAssemblyBucketKey = require(config, "cloudAssemblyBucketKey")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def artifactsRoleArn = require(config, "artifactsRoleArn")
  def pipelines = require(config, "pipelines")

  withAwsRole(artifactsRoleArn) {
    // See https://stackoverflow.com/a/47027926 for loop.
    for (int i = 0; i < pipelines.size(); i++) {
      def pipelineName = pipelines[i]

      configureCloudAssemblyV2(
        cloudAssemblyBucketKey: cloudAssemblyBucketKey,
        artifactsBucketName: artifactsBucketName,
        pipelineName: pipelineName,
      )

      triggerPipeline(
        artifactsBucketName: artifactsBucketName,
        pipelineName: pipelineName,
      )
    }
  }
}

def configureCdkSource(Map config) {
  def cdkSourceBucketKey = require(config, "cdkSourceBucketKey")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def pipelineName = require(config, "pipelineName")

  def json = groovy.json.JsonOutput.toJson([
    bucketName: artifactsBucketName,
    bucketKey: cdkSourceBucketKey,
  ])

  writeFile(file: "cdk-source.json", text: json)
  sh "aws s3 cp cdk-source.json s3://$artifactsBucketName/pipelines/$pipelineName/cdk-source.json"
}

def configureCloudAssembly(Map config) {
  def cloudAssemblyBucketKey = require(config, "cloudAssemblyBucketKey")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def pipelineName = require(config, "pipelineName")
  def cdkParameters = require(config, "parameters")
  def environments = require(config, "environments")

  def json = groovy.json.JsonOutput.toJson([
    cloudAssemblyBucketName: artifactsBucketName,
    cloudAssemblyBucketKey: cloudAssemblyBucketKey,
    environments: environments.collect { key, value ->
      [name: key, stackNames: value]
    },
    parameters: cdkParameters.collect { key, value ->
      [
        name: key,
        value: [
          type: "variable",
          variable: value,
        ],
      ]
    },
  ])

  writeFile(file: "cloud-assembly.json", text: json)
  sh "aws s3 cp cloud-assembly.json s3://$artifactsBucketName/pipelines/$pipelineName/cloud-assembly.json"
}

def configureCloudAssemblyV2(Map config) {
  def cloudAssemblyBucketKey = require(config, "cloudAssemblyBucketKey")
  def artifactsBucketName = require(config, "artifactsBucketName")
  def pipelineName = require(config, "pipelineName")

  def json = groovy.json.JsonOutput.toJson([
    cloudAssemblyBucketName: artifactsBucketName,
    cloudAssemblyBucketKey: cloudAssemblyBucketKey,
  ])

  writeFile(file: "cloud-assembly.json", text: json)
  sh "aws s3 cp cloud-assembly.json s3://$artifactsBucketName/pipelines/$pipelineName/cloud-assembly.json"
}

def configureVariablesAndTrigger(Map config) {
  def artifactsRoleArn = require(config, "artifactsRoleArn")

  withAwsRole(artifactsRoleArn) {
    configureVariables(config)
    triggerPipeline(config)
  }
}

def configureVariables(Map config) {
  def artifactsBucketName = require(config, "artifactsBucketName")
  def pipelineName = require(config, "pipelineName")
  def variables = require(config, "variables")
  def variablesNamespace = config["variablesNamespace"] // optional

  def json = groovy.json.JsonOutput.toJson(variables)

  def fileName = variablesNamespace ? "variables-${variablesNamespace}.json" : "variables.json"

  writeFile(file: "variables.json", text: json)
  sh "aws s3 cp variables.json s3://$artifactsBucketName/pipelines/$pipelineName/$fileName"
}

def triggerPipeline(Map config) {
  def artifactsBucketName = require(config, "artifactsBucketName")
  def pipelineName = require(config, "pipelineName")

  // Upload a blank file to trigger the event.
  sh """
    rm -f /tmp/trigger
    echo >/tmp/trigger
    aws s3 cp /tmp/trigger "s3://$artifactsBucketName/pipelines/$pipelineName/trigger"
  """
}

private def require(Map config, String name) {
  if (!config.containsKey(name)) {
    throw new Exception("Missing $name")
  }
  return config[name]
}
