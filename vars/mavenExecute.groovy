import static com.sap.piper.Prerequisites.checkScript

import com.sap.piper.ConfigurationHelper
import com.sap.piper.Utils

import groovy.transform.Field

@Field def STEP_NAME = getClass().getName()

@Field Set GENERAL_CONFIG_KEYS = []
@Field Set STEP_CONFIG_KEYS = [
    'dockerImage',
    'globalSettingsFile',
    'projectSettingsFile',
    'pomPath',
    'm2Path'
]
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS.plus([
    'dockerOptions',
    'flags',
    'goals',
    'defines',
    'isEvaluateExpression',
    'logSuccessfulMavenTransfers'
])

void call(Map parameters = [:]) {
    handlePipelineStepErrors(stepName: STEP_NAME, stepParameters: parameters) {

        final script = checkScript(this, parameters) ?: this

        // load default & individual configuration
        Map configuration = ConfigurationHelper.newInstance(this)
            .loadStepDefaults()
            .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
            .mixinStepConfig(script.commonPipelineEnvironment, STEP_CONFIG_KEYS)
            .mixinStageConfig(script.commonPipelineEnvironment, parameters.stageName?:env.STAGE_NAME, STEP_CONFIG_KEYS)
            .mixin(parameters, PARAMETER_KEYS)
            .use()

        new Utils().pushToSWA([
            step: STEP_NAME,
            stepParamKey1: 'scriptMissing',
            stepParam1: parameters?.script == null
        ], configuration)

        String command = "mvn"
        def commandOptions = []

        def globalSettingsFile = configuration.globalSettingsFile
        if (globalSettingsFile?.trim()) {
            if(globalSettingsFile.trim().startsWith("http")){
                downloadSettingsFromUrl(globalSettingsFile)
                globalSettingsFile = "settings.xml"
            }
            commandOptions += "--global-settings '${globalSettingsFile}'"
        }

        def m2Path = configuration.m2Path
        if(m2Path?.trim()) {
            commandOptions += "-Dmaven.repo.local='${m2Path}'"
        }

        def projectSettingsFile = configuration.projectSettingsFile
        if (projectSettingsFile?.trim()) {
            if(projectSettingsFile.trim().startsWith("http")){
                downloadSettingsFromUrl(projectSettingsFile)
                projectSettingsFile = "settings.xml"
            }
            commandOptions += "--settings '${projectSettingsFile}'"
        }

        def pomPath = configuration.pomPath
        if(pomPath?.trim()){
            commandOptions += "--file '${pomPath}'"
        }

        def mavenFlags = configuration.flags
        if (mavenFlags?.trim()) {
            commandOptions += "${mavenFlags}"
        }

        // Always use Maven's batch mode
        if (!(commandOptions =~ /--batch-mode|-B(?=\s)|-B\\|-B$/)) {
            commandOptions += '--batch-mode'
        }

        // Disable log for successful transfers by default. Note this requires the batch-mode flag.
        final String disableSuccessfulMavenTransfersLogFlag = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn'
        if (!configuration.logSuccessfulMavenTransfers) {
            if (!command.contains(disableSuccessfulMavenTransfersLogFlag)) {
                commandOptions += disableSuccessfulMavenTransfersLogFlag
            }
        }

        def mavenGoals = configuration.goals
        if (mavenGoals?.trim()) {
            commandOptions += "${mavenGoals}"
        }
        def defines = configuration.defines
        if (defines?.trim()){
            commandOptions += "${defines}"
        }
        if(!configuration.isEvaluateExpression){
          dockerExecute(script: script, dockerImage: configuration.dockerImage, dockerOptions: configuration.dockerOptions) {
            sh "${command} ${commandOptions.join(\" \")}"
          }
        } else{
           dockerExecute(script: script, dockerImage: configuration.dockerImage, dockerOptions: configuration.dockerOptions) {
            body()
          }  
        }
    }
}

private downloadSettingsFromUrl(String url){
    def settings = httpRequest url
    writeFile file: 'settings.xml', text: settings.getContent()
}

