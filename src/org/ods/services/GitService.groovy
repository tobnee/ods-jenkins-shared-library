package org.ods.services

class GitService {

    @SuppressWarnings('NonFinalPublicField')
    public static String ODS_GIT_TAG_BRANCH_PREFIX = 'ods-generated-'

    private final def script

    GitService(script) {
        this.script = script
    }

    static String getReleaseBranch(String version) {
        "release/${ODS_GIT_TAG_BRANCH_PREFIX}${version}"
    }

    String getOriginUrl() {
        script.sh(
            returnStdout: true,
            script: 'git config --get remote.origin.url',
            label: "Get Git URL of remote 'origin'"
        ).trim()
    }

    String getCommitSha() {
        script.sh(
            returnStdout: true,
            script: 'git rev-parse HEAD',
            label: 'Get Git commit SHA'
        ).trim()
    }

    String getCommitAuthor() {
        script.sh(
            returnStdout: true,
            script: "git --no-pager show -s --format='%an (%ae)' HEAD",
            label: 'Get Git commit author'
        ).trim()
    }

    String getCommitMessage() {
        script.sh(
            returnStdout: true,
            script: 'git log -1 --pretty=%B HEAD',
            label: 'Get Git commit message'
        ).trim()
    }

    String getCommitTime() {
        script.sh(
            returnStdout: true,
            script: 'git show -s --format=%ci HEAD',
            label: 'Get Git commit timestamp'
        ).trim()
    }

    /** Looks in commit message for string '[ci skip]', '[ciskip]', '[ci-skip]' and '[ci_skip]'. */
    boolean isCiSkipInCommitMessage() {
        return script.sh(
            returnStdout: true, script: 'git show --pretty=%s%b -s',
            label: 'check skip CI?'
        ).toLowerCase().replaceAll('[\\s\\-\\_]', '').contains('[ciskip]')
    }

    void checkout(String gitCommit, def userRemoteConfigs) {
        def gitParams = [
            $class: 'GitSCM',
            branches: [[name: gitCommit]],
            doGenerateSubmoduleConfigurations: false,
            submoduleCfg: [],
            userRemoteConfigs: userRemoteConfigs,
        ]
        if (isSlaveNodeGitLfsEnabled()) {
            gitParams.extensions = [
                [$class: 'GitLFSPull']
            ]
        }
        script.checkout(gitParams)
    }

    def configureUser() {
        script.sh(
            script: """
                git config --global user.email 'undefined'
                git config --global user.name 'ODS Jenkins Shared Library System User'
                """,
            label: 'configure git system user'
        )
    }

    def createTag(String name) {
        script.sh(
          script: """git tag -a -m "${name}" ${name}""",
            label: "tag with ${name}"
        )
    }

    def pushTag(String name) {
        script.sh(
            script: "git push origin ${name}",
            label: "push tag ${name}"
        )
    }

    def pushBranchWithTags(String name) {
        script.sh(
            script: "git push --tags origin ${name}",
            label: "push branch ${name} with tags"
        )
    }

    def checkout(
        String gitRef,
        def extensions,
        def userRemoteConfigs,
        boolean doGenerateSubmoduleConfigurations = false) {
        script.checkout([
            $class: 'GitSCM',
            branches: [[name: gitRef]],
            doGenerateSubmoduleConfigurations: doGenerateSubmoduleConfigurations,
            extensions: extensions,
            userRemoteConfigs: userRemoteConfigs,
        ])
    }

    boolean remoteTagExists(String name) {
        def tagStatus = script.sh(
            script: "git ls-remote --exit-code --tags origin ${name} &>/dev/null",
            label: "check if tag ${name} exists",
            returnStatus: true
        )
        tagStatus == 0
    }

    boolean localTagExists(String name) {
        def tagStatus = script.sh(
            script: "git rev-parse ${name} &>/dev/null",
            label: "check if tag ${name} exists",
            returnStatus: true
        )
        tagStatus == 0
    }

    boolean localBranchExists(String name) {
        branchExists("refs/heads/${name}")
    }

    boolean remoteBranchExists(String name) {
        branchExists("refs/remotes/origin/${name}")
    }

    boolean branchExists(String name) {
        def branchCheckStatus = script.sh(
            script: """git show-ref --verify --quiet ${name}""",
            returnStatus: true,
            label: "Check if ${name} already exists"
        )
        return branchCheckStatus == 0
    }

    def checkoutNewLocalBranch(String name) {
        // Local state might have a branch from previous, failed pipeline runs.
        // If so, we'd rather start from a clean state.
        if (localBranchExists(name)) {
            script.sh(
                script: "git branch -D ${name}",
                label: "delete local ${name} branch"
            )
        }
        script.sh(
            script: "git checkout -b ${name}",
            label: "create new ${name} branch"
        )
    }

    String readBaseTagList(String version, String changeId, String envToken) {
        def previousEnvToken = 'D'
        if (envToken == 'P') {
            previousEnvToken = 'Q'
        }
        def tagPattern = "${ODS_GIT_TAG_BRANCH_PREFIX}v${version}-${changeId}-[0-9]*-${previousEnvToken}"
        script.sh(
            script: "git tag --list '${tagPattern}'",
            returnStdout: true,
            label: "list tags for version ${version}-${changeId}-*-${previousEnvToken}"
        ).trim()
    }

    private boolean isSlaveNodeGitLfsEnabled() {
        def statusCode = script.sh(
            script: 'git lfs &> /dev/null',
            label: 'Check if Git LFS is enabled',
            returnStatus: true
        )
        return statusCode == 0
    }

}