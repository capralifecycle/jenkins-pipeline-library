#!/usr/bin/groovy
package no.capraconsulting.buildtools.webappv1pipeline

/**
 * This method allows for easy setup of serverless pipelines using Java
 * with deployment to two different environments (systest and prod) with
 * a release step in betwen and manual confirmation for release and deploy
 * to prod.
 */
def pipeline(Closure cl) {
  def config = new ConfigDelegate()
  cl.resolveStrategy = Closure.DELEGATE_FIRST
  cl.delegate = config
  cl()

  def currentVersion
  def artifactId

  def publish = true // FIXME: check for any possible deployment
  def s3Uri

  dockerNode {
    checkout scm

    // TODO: Allow to customize
    def img = docker.image('node:9')
    img.pull() // ensure we have latest version
    img.inside {
      withEnv(['CI=true']) {
        config.build()
      }
    }

    if (publish) {
      s3Uri = publishBundleToS3(config.distDir, config.buildArtifactRepo)
    }
  }

  // TODO: Reuse existing node if possible
  int nextMilestone = 1
  for (deployment in config.deployments) {
    if (!env.BRANCH_NAME.matches(deployment.branches)) {
      break
    }

    if (deployment.waitForInput) {
      milestone nextMilestone++
      if (!askDeployConfirm(config, deployment.name)) {
        break
      }
    }

    lock(resource: "deploy-${env.JOB_NAME}-${deployment.name}", inversePrecedence: true) {
      milestone nextMilestone++
      stage("Deploy to ${deployment.name}") {
        dockerNode {
          deployToEnv(config.buildArtifactRepo, s3Uri, deployment)
        }
      }
    } // end lock
  } // end deployProd
}

// TODO: Improve ordering of methods

def generateDistArtifact(String distDir) {
  echo "Generating dist artifact file"
  sh """
    target=\"\$(pwd)/dist.tgz\"
    cd \"$distDir\"
    tar zcf \"\$target\" .
  """

  return pwd() + '/dist.tgz'
}

def publishBundleToS3(String distDir, S3Env repo) {
  artifactS3Path = {
    def gitv = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
    def date = sh(script: 'date --utc +%Y%m%d.%H%M%S', returnStdout: true).trim()
    def filename = "$date-$gitv-dist.tgz"

    path = deployment.path
    if (deployment.path && deployment.path[-1] != '/') {
      path += '/'
    }
    path += filename
  }()

  def localPath = generateDistArtifact(config.distDir)
  def s3Uri = "s3://${repo.bucket}/$artifactS3Path"

  echo "Uploading dist to $s3Uri"
  withAwsAssumeRole(repo.iamRoleArn) {
    insideAwsCli {
      sh "aws --region '${repo.region}' s3 cp '$localPath' '$s3Uri'"
    }
  }

  return s3Uri
}

def askDeployConfirm(ConfigDelegate config, String env) {
  stage("Asking to deploy to $env") {
    try {
      timeout(config.inputTimeout) {
        input(
          id: 'prod',
          message: "Deploy to $env?"
        )
      }
      return true
    } catch (ignored) {
      echo "Skipping deployment to $env - aborting job"
      currentBuild.result = 'ABORTED'
      return false
    }
  }
}

def deployToEnv(S3Env repo, String s3Uri, Deployment deployment) {
  // TODO: Optimize deployment on same node still having uploaded file
  echo "Downloading $s3Uri"
  withAwsAssumeRole(repo.iamRoleArn) {
    insideAwsCli {
      sh "aws --region '${repo.region}' s3 cp '$s3Uri' ./dist.tgz"
    }
  }

  echo "Unpacking"
  sh """
    set -eux
    rm -rf dist-deploy
    mkdir dist-deploy
    cd dist-deploy
    tar zxf ../dist.tgz
  """

  deployToS3(
    iamRoleArn: deployment.target.iamRoleArn,
    region: deployment.target.region,
    bucket: deployment.target.bucket,
    src: './dist-deploy/'
  )

  // TODO: Logic for removing old files

  // TODO: Should probably not change modification time of files without new
  // content as it will invalidate ETag for some servers etc (not sure about S3).

  if (deployment.target.cloudFrontDistributionId) {
    // TODO: invalidate CF cache
    // invalidateCloudFrontCache(iamRoleArn: "abcd", distributionId: deployment.target.cloudFrontDistributionId)
  }
}

// TODO: Not in use but useful later. Move another place for later reuse
def configureNpmNexusCredentials(Map params) {
  checkRequiredParams(params, [
    'credentialsId',
  ])

  withCredentials([
    usernamePassword(
      credentialsId: params.get('credentialsId'),
      passwordVariable: 'NEXUS_PASSWORD',
      usernameVariable: 'NEXUS_USERNAME'
    )
  ]) {
    def encoded = "${env.NEXUS_USERNAME}:${env.NEXUS_PASSWORD}".bytes.encodeBase64().toString()

    def text = ""
    text += "email=it@capraconsulting.no\n"
    text += "_auth=$encoded"
    writeFile(file: '.npmrc', text: text)
  }
}

def checkRequiredParams(Map params, List required) {
  required.each {
    if (!params.containsKey(it)) {
      throw Exception("Missing $it from parameter list")
    }
  }
}

def generator(String alphabet, int n) {
  new Random().with {
    (1..n).collect { alphabet[nextInt(alphabet.length())] }.join()
  }
}

def tempFileName() {
  pwd(tmp: true) + '/' + generator(('a'..'z').join(), 15)
}

def withAwsAssumeRole(roleArn, body) {
  // Jenkins do not currently support masking arbitrary data from logs.
  // See https://issues.jenkins-ci.org/browse/JENKINS-36007
  // To avoid unwanted leaking of credentials in logs we avoid storing
  // credentials as environment variables.

  def tempCredentialFile = tempFileName()

  def img = docker.image("923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/aws-cli")
  img.pull() // ensure we have latest version
  img.inside("-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI=${env.AWS_CONTAINER_CREDENTIALS_RELATIVE_URI}") {
    sh """
      set -eux
      data=\$(aws sts assume-role \
                --role-arn \"$roleArn\" \
                --role-session-name jenkins-assumed-role-$(date +%s)

      aws_access_key_id=\$(echo \"\$data\" | jq -r '.Credentials.AccessKeyId')
      aws_secret_access_key=\$(echo \"\$data\" | jq -r '.Credentials.SecretAccessKey')
      aws_session_token=\$(echo \"\$data\" | jq -r '.Credentials.SessionToken')

      config=\$(
        echo \"[default]\"
        echo \"aws_access_key_id=\$aws_access_key_id\"
        echo \"aws_secret_access_key=\$aws_secret_access_key\"
        echo \"aws_session_token=\$aws_session_token\"
      )

      echo \"\$config\" >\"$tempCredentialFile\"
    """
  }

  try {
    withEnv([
      "AWS_SHARED_CREDENTIALS_FILE=$tempCredentialFile"
    ]) {
      body()
    }
  } finally {
    sh "rm -f '$tempCredentialFile'"
  }
}

def deployToS3(params) {
  checkRequiredParams(params, [
    'bucket',
    'iamRoleArn',
    'region',
    'src',
  ])

  echo 'Deploying to S3'
  withAwsAssumeRole(params.get('iamRoleArn')) {
    insideAwsCli {
      sh "aws --region '${params.get('region')}' s3 sync '${params.get('src')} s3://${params.get('bucket')}/ --acl public-read"
    }
  }
}

def insideAwsCli(body) {
  def img = docker.image("923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/aws-cli")
  img.pull() // ensure we have latest version
  img.inside {
    body()
  }
}

def invalidateCloudFrontCache(params) {
  checkRequiredParams(params, [
    'iamRoleArn',
    'distributionId',
  ])

  echo 'Invalidating CloudFront cache'

  withAwsAssumeRole(params.get('iamRoleArn')) {
    insideAwsCli {
      // AWS CLI support for this service is only available in a preview stage.
      sh 'aws configure set preview.cloudfront true'

      sh "aws cloudfront create-invalidation --distribution-id=\"${params.get('distributionId')}\" --paths '/*'"
    }
  }
}

class ConfigDelegate implements Serializable {
  Closure build
  S3Env buildArtifactRepo
  String distDir = 'dist'
  List deployments = []

  S3DeployEnv deployDevtestEnv
  String deployDevtestBranch = 'master'

  S3DeployEnv deployProdEnv
  String deployProdBranch = 'master'

  Map inputTimeout = [time: 3, unit: 'DAYS']
}

class Deployment implements Serializable {
  String branches // regex pattern
  String dir = ''
  String name
  S3DeployEnv target
  Boolean waitForInput = false
}

class S3Env implements Serializable {
  String iamRoleArn
  String bucket
  String region
}

class S3DeployEnv extends S3Env {
  String cloudFrontDistributionId // optional
}
