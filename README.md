# Jenkins2 pipeline library

[![Build Status](https://jenkins.capra.tv/buildStatus/icon?job=jenkins-pipeline-library/master)](https://jenkins.capra.tv/job/jenkins-pipeline-library/job/master/)

Used to store common code used in Jenkins pipelines.

This is set up as a library named 'cals' in Jenkins.

https://jenkins.io/doc/book/pipeline/shared-libraries/

Inspiration sources:

- https://github.com/fabric8io/fabric8-pipeline-library/tree/master/vars

## Adding changes

The `master` branch is protected, so changes will have to be made
in a branch first, then passing as a build, before updating `master` to
point to that commit. No separate merge commit is needed.

The status of the commit can be tracked by comparing changes to master,
e.g. https://github.com/capralifecycle/jenkins-pipeline-library/compare/next
if pushing to the `next` branch.

This is done to reduce the probability that changes cause all
build in Jenkins fail due to introducing a faulty change.

## Using the library

The main use of the library is exporting methods that can be used in other
pipelines to keep things DRY. All files present in `vars` directory will
be available as methods when this library is imported.

The best way to get started is looking at other projects. Examples:

- https://github.com/capralifecycle/capra-tv/blob/master/Jenkinsfile
- https://github.com/capralifecycle/jenkins-master/blob/master/Jenkinsfile
- https://github.com/capralifecycle/jenkins-slave/blob/master/Jenkinsfile
- https://github.com/capralifecycle/sonarqube-docker/blob/master/Jenkinsfile

## Testing changes to pipeline library

If changes to the library causes it to fail running it will most probably
break most builds in Jenkins.

A safe way of testing changes:

- Push changes to a feature branch

- Reference the branch when using the library:

```groovy
// In Jenkinsfile of another repo
@Library('cals@my-feature-branch') _
```

If you don't want to modify `master` of another project just to test it,
you should be able to apply it to a branch in that project as well as
long as it will automatically build in Jenkins.
