#!/usr/bin/env groovy
import groovy.text.GStringTemplateEngine
import groovy.text.Template

/**
 * @author Maksym Bryzhko
 * @version 0.1
 */

class CrowdinCliWraper {
	static String CROWDIN_LIB_VERSION = "0.5.1"
	String[] args
	String workingDir
	String crowdinProjectApiKey

	CrowdinCliWraper(args) {
		this.args = args
		this.workingDir = retrieveWorkingDir()
		this.crowdinProjectApiKey = retrieveApiKey()
		println("Working Dir ${this.workingDir}")
	}

	private retrieveApiKey() {
		System.getenv("CROWDINW_PROJECT_API_KEY")
	}

	private String retrieveWorkingDir() {
		def crowdinwHome = System.getenv("CROWDINW_HOME")
		println("CROWDINW_HOME is $crowdinwHome")
		def workingDirPath = crowdinwHome ?: "."
		def workingDir = new File(workingDirPath)
		if (!workingDir.exists()) throw new RuntimeException("Corwdin home dir does not exists: $workingDir")

		return workingDir.canonicalPath
	}

	def execute() {
		CliBuilder cliBuilder = new CliBuilder(usage: "crowdinw.groovy <arguments>")
		cliBuilder.c(longOpt:'config', args:1, argName:'crowdinConfig', 'Project-specific configuration file')
		cliBuilder.t(longOpt:'conf-template', args:2, argName:'crowdinConfigTemplate', 'Template of project-specific configuration file')
		def options = cliBuilder.parse(args)
		if (!options.arguments()) {
			cliBuilder.usage()
		} else {
			println("Executing Crowdin Wrapper with arguments: ${options.arguments()}")
			runCrowdinCliTool(crowdinCliToolProcessBuilder(options))
		}
	}

	private def runCrowdinCliTool(ProcessBuilder processBuilder) {
		println("Running Crowdin CLI Tool: ${processBuilder.command()}")

		def process  = processBuilder.start()
		process.inputStream.eachLine {println it}
	}

	private ProcessBuilder crowdinCliToolProcessBuilder(OptionAccessor opts) {
		List<String> crowdinCliToolRunCommand = baseCrowdinExecCommand()
		crowdinCliToolRunCommand.addAll(configLocation(opts))
		crowdinCliToolRunCommand.addAll(argumentsFromCommandLine(opts))

		new ProcessBuilder(crowdinCliToolRunCommand).redirectErrorStream(true)
	}

	private List<String> configLocation(OptionAccessor opts) {
		if (opts.getProperty("t")) {
			["-c", pathToconfigFromTemplate(opts)]
		} else if (opts.getProperty("c")) {
			["-c", opts.getProperty("c").toString()]
		} else {
			["-c", "${workingDir}/crowdin/crowdin.yaml".toString()]
		}
	}

	/**
	 * Create config from template and return path to the config.
	 */
	private String pathToconfigFromTemplate(OptionAccessor opts) {
		def templateFile = new File(opts.getProperty("t").toString())

		Template template = new GStringTemplateEngine().createTemplate(templateFile)

		File crowdinConfigFile = File.createTempFile("crowdin", "yaml")
		crowdinConfigFile << template.make([basePath: workingDir, apiKey: crowdinProjectApiKey])
		crowdinConfigFile.absolutePath
	}

	private List<String> argumentsFromCommandLine(OptionAccessor opts) {
		opts.arguments()
	}

	private ArrayList<String> baseCrowdinExecCommand() {
		["java", "-jar", "${workingDir}/crowdin/lib/${CROWDIN_LIB_VERSION}/crowdin-cli.jar".toString()]
	}

}

new CrowdinCliWraper(args).execute()
