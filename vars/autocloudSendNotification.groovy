
import static com.sap.piper.Prerequisites.checkScript

import com.sap.piper.ConfigurationHelper
import com.sap.piper.GenerateDocumentation
import com.sap.piper.Utils
import groovy.text.GStringTemplateEngine
import groovy.transform.Field

@Field String STEP_NAME = getClass().getName()
@Field Set GENERAL_CONFIG_KEYS = [
    /**
     * Only if `notifyCulprits` is set:
     * Credentials if the repository is protected.
     * @possibleValues Jenkins credentials id
     */
    'gitSshKeyCredentialsId'
]
@Field Set STEP_CONFIG_KEYS = GENERAL_CONFIG_KEYS.plus([
    /**
     * Set the build result used to determine the mail template.
     * default `currentBuild.result`
     */
    'buildResult',
    /**
     * Only if `notifyCulprits` is set:
     * Defines a dedicated git commitId for culprit retrieval.
     * default `commonPipelineEnvironment.getGitCommitId()`
     */
    'gitCommitId',
    /**
     * Only if `notifyCulprits` is set:
     * Repository url used to retrieve culprit information.
     * default `commonPipelineEnvironment.getGitSshUrl()`
     */
    'gitUrl',
    /**
     * defines if the console log file should be attached to the notification mail.
     * @possibleValues `true`, `false`
     */
    'notificationAttachment',
    /**
     * A space-separated list of recipients that always get the notification.
     */
    'notificationRecipients',
    /**
     * Notify all committers since the last successful build.
     * @possibleValues `true`, `false`
     */
    'notifyCulprits',
    /**
     * Number of log line which are included in the email body.
     */
    'numLogLinesInBody',
    /**
     * The project name used in the email subject.
     * default `currentBuild.fullProjectName`
     */
    'projectName',
    /**
     * Needs to be set to `true` if step is used outside of a node context, e.g. post actions in a declarative pipeline script.
     * default `false`
     * @possibleValues `true`, `false`
     */
    'wrapInNode'
])
@Field Set PARAMETER_KEYS = STEP_CONFIG_KEYS

/**
 * Sends notifications to all potential culprits of a current or previous build failure and to fixed list of recipients.
 * It will attach the current build log to the email.
 *
 * Notifications are sent in following cases:
 *
 * * current build failed or is unstable
 * * current build is successful and previous build failed or was unstable
 */
@GenerateDocumentation
void call(Map parameters = [:]) {
	handlePipelineStepErrors (stepName: STEP_NAME, stepParameters: parameters, allowBuildFailure: true) {
        def script = checkScript(this, parameters) ?: this
        String stageName = parameters.stageName ?: env.STAGE_NAME
		// load default & individual configuration
        Map config = ConfigurationHelper.newInstance(this)
            .loadStepDefaults([:], stageName)
            .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
            .mixinStepConfig(script.commonPipelineEnvironment, STEP_CONFIG_KEYS)
            .mixinStageConfig(script.commonPipelineEnvironment, stageName, STEP_CONFIG_KEYS)
            .mixin(
                projectName: script.currentBuild.fullProjectName,
                displayName: script.currentBuild.displayName,
                buildResult: script.currentBuild.result,
                gitUrl: script.commonPipelineEnvironment.getGitSshUrl(),
                gitCommitId: script.commonPipelineEnvironment.getGitCommitId()
            )
            .mixin(parameters, PARAMETER_KEYS)
            .use()

        new Utils().pushToSWA([step: STEP_NAME], config)
		def emailBody = """     Hello,</br></br>
                            ${config.projectName} ${config.displayName} - Build # $BUILD_NUMBER ${config.buildResult}</br></br>
                            Please check console output <a href='$BUILD_URL'>here</a> to view the details.</br></br></br>
                            Regards,</br>
                            COS4Auto DevOps Team
                                        """
		def emailSubject = "${config.projectName} ${config.displayName}- Jenkins Build ${config.buildResult}"
		
		emailext body: "${emailBody}", subject: "${emailSubject}", to: "${env.GIT_LAST_COMMITTER_EMAIL}"
	}
}
return this
