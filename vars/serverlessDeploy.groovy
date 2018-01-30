#!/usr/bin/groovy

/**
 * Deploy a service using Serverless.
 */
def call(deployIamRole, serverlessArgs) {
  // AWS_CONTAINER_CREDENTIALS_RELATIVE_URI contains a variable
  // used by awscli to pick up the ECS task role instead of using
  // instance role. This lets us use the task role of the jenkins slave.
  docker.image('923402097046.dkr.ecr.eu-central-1.amazonaws.com/buildtools/serverless').inside("-e AWS_CONTAINER_CREDENTIALS_RELATIVE_URI=${env.AWS_CONTAINER_CREDENTIALS_RELATIVE_URI}") {
    sh '''
      #!/bin/bash
      set +x
      ls -al
      ls -al target
      if [ -d "target/checkout" ]; then
        ls -al target/checkout
      fi
      if [ -d "target/checkout/target" ]; then
        ls -al target/checkout/target
      fi
      
      echo "serverless deploy ''' + serverlessArgs + '''"'''

    sh '''
      #!/bin/bash
      set +x
      
      CREDS=$(aws sts assume-role --role-arn \\
        ''' + deployIamRole + ''' \\
        --role-session-name serverless-deploy-session --out json)
      export AWS_ACCESS_KEY_ID=$(echo $CREDS | jq -r '.Credentials.AccessKeyId')
      export AWS_SECRET_ACCESS_KEY=$(echo $CREDS | jq -r '.Credentials.SecretAccessKey')
      export AWS_SESSION_TOKEN=$(echo $CREDS | jq -r '.Credentials.SessionToken')
      
      # Must set HOME as it is not set and thus serverless will default to root dir which the user does not have read/write access to
      export HOME=$(pwd); serverless config credentials --provider aws --key $AWS_ACCESS_KEY_ID --secret $AWS_SECRET_ACCESS_KEY ''' + serverlessArgs + '''
      ls -al target
      serverless deploy ''' + serverlessArgs + ''' '''
  }
}
