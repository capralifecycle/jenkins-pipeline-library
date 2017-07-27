# Jenkins2 pipeline library

Used to store common code used in Jenkins pipelines.

This is set up as a library named 'cals' in Jenkins.

https://jenkins.io/doc/book/pipeline/shared-libraries/

Inspiration sources:

- https://github.com/fabric8io/fabric8-pipeline-library/tree/master/vars

## Adding changes

The `master` branch is protected, so changes will have to be made
in a branch first, before updating `master` to point to that commit.

This is done to reduce the probability that changes cause all
build in Jenkins fail due to introducing a faulty change.

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
