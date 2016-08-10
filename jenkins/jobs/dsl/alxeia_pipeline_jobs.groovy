// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
def referenceAppGitRepo = "alexia"
def referenceAppGitUrl = "ssh://jenkins@gerrit:29418/${PROJECT_NAME}/" + referenceAppGitRepo

// Jobs
def getCode = freeStyleJob(projectFolderName + "/Get_Code")
def Install = freeStyleJob(projectFolderName + "/Install")
def lint = freeStyleJob(projectFolderName + "/Lint")
def test = freeStyleJob(projectFolderName + "/Test")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Example_Alexia_Pipeline")

pipelineView.with{
    title('Example Alexia Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/Get_Code")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

getCode.with{
  description("This job downloads the code from Git.")
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  scm{
    git{
      remote{
        url(referenceAppGitUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/master")
    }
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  triggers{
    gerrit{
      events{
        refUpdated()
      }
      configure { gerritxml ->
        gerritxml / 'gerritProjects' {
          'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.GerritProject' {
            compareType("PLAIN")
            pattern(projectFolderName + "/" + referenceAppgitRepo)
            'branches' {
              'com.sonyericsson.hudson.plugins.gerrit.trigger.hudsontrigger.data.Branch' {
                compareType("PLAIN")
                pattern("master")
              }
            }
          }
        }
        gerritxml / serverName("ADOP Gerrit")
      }
    }
  }
  label("docker")
  steps {
    shell('''set -xe
            |echo Pull the code from Git 
            |'''.stripMargin())
  }
  publishers{
    archiveArtifacts("**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/Install"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD",'${JOB_NAME}')
        }
      }
    }
  }
}

Install.with{
  description("This job performs an npm install")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Get_Code","Parent build name")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("docker")
  steps {
    copyArtifacts('Get_Code') {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set -x
            |echo Run an install 
            |
            |docker run \\
            |		--rm \\
            |		-v /var/run/docker.sock:/var/run/docker.sock \\
            |		-v jenkins_slave_home:/jenkins_slave_home/ \\
            |		node
            |		npm install --save	
            |'''.stripMargin())
  }
  publishers{
    archiveArtifacts("**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/Package_SQL"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${JOB_NAME}')
        }
      }
    }
  }
}


lint.with{
  description("This job will package the SQL for use in other environments")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Get_Code","Parent build name")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("docker")
  steps {
    copyArtifacts('Get_Code') {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set -x
            |echo Run an install 
            |
            |docker run \\
            |		--rm \\
            |		-v /var/run/docker.sock:/var/run/docker.sock \\
            |		-v jenkins_slave_home:/jenkins_slave_home/ \\
            |		node
            |		npm lint
            |'''.stripMargin())
  }
  publishers{
    archiveArtifacts("**/*zip")
    downstreamParameterized{
      trigger(projectFolderName + "/ST_Deploy"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD", '${JOB_NAME}')
        }
      }
    }
  }
}

test.with{
  description("When triggered this will deploy to the ST environment.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Get_Code","Parent build name")
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("docker")
  steps {
    copyArtifacts("Package_SQL") {
        buildSelector {
          buildNumber('${B}')
      }
    }
    shell('''set -x
            |echo Run an install 
            |
            |docker run \\
            |		--rm \\
            |		-v /var/run/docker.sock:/var/run/docker.sock \\
            |		-v jenkins_slave_home:/jenkins_slave_home/ \\
            |		node
            |		npm run test
            |'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Image_Test"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

