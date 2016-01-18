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
	CliExecutor crowdinCliToolExecuter

	CrowdinCliWraper(args) {
		this.args = args
		this.workingDir = retrieveWorkingDir()
		this.crowdinProjectApiKey = retrieveApiKey()
		this.crowdinCliToolExecuter = new CliExecutor()
		println("Using working dir ${this.workingDir}")
	}

	private retrieveApiKey() {
		System.getenv("CROWDINW_PROJECT_API_KEY")
	}

	private String retrieveWorkingDir() {
		def crowdinwHome = System.getenv("CROWDINW_HOME")
		def workingDirPath = crowdinwHome ?: "."
		def workingDir = new File(workingDirPath)
		if (!workingDir.exists()) throw new RuntimeException("Corwdin home dir does not exists: $workingDir")

		return workingDir.canonicalPath
	}

	def execute() {
		CliBuilder cliBuilder = new CliBuilder(usage: "crowdinw.groovy <arguments>")
		cliBuilder.c(longOpt:'config', args:1, argName:'crowdinConfig', 'Project-specific configuration file')
		cliBuilder.t(longOpt:'conf-template', args:1, argName:'crowdinConfigTemplate', 'Template of project-specific configuration file')
		cliBuilder._(longOpt:'commit', 'Commit translation into repo')
		def options = cliBuilder.parse(args)
		if (!options.arguments()) {
			cliBuilder.usage()
		} else {
			println("Executing Crowdin Wrapper with arguments: ${options.arguments()}")
			crowdinCliToolExecuter.execute(crowdinCliToolProcessBuilder(options))
			commitTranslationIfNeeded(options)

		}
	}

	private def commitTranslationIfNeeded(OptionAccessor opts) {
		if(opts.getProperty("commit")) {
			def vcsAdapter = createVcsAdapter()
			vcsAdapter.commitChanges("Crowdin Sync")
		}
	}

	private VcsAdapter createVcsAdapter() {
		def vcsAdapter = new GitAdapter()
		if (!vcsAdapter.isSupported(new File(workingDir))) {
			throw new RuntimeException("Git Repo did not found")
		}
		return vcsAdapter
	}

	private ProcessBuilder crowdinCliToolProcessBuilder(OptionAccessor opts) {
		List<String> crowdinCliToolRunCommand = baseCrowdinExecCommand()
		crowdinCliToolRunCommand.addAll(configLocation(opts))
		crowdinCliToolRunCommand.addAll(argumentsFromCommandLine(opts))

		new ProcessBuilder(crowdinCliToolRunCommand)
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

class CliExecutor {
	public def execute(ProcessBuilder processBuilder) throws RuntimeException {
		println("Running ${processBuilder.command()}")

		StringBuffer out = new StringBuffer()
		StringBuffer err = new StringBuffer()

		def process  = processBuilder.start()

		process.waitForProcessOutput(out, err)

		println("Running ${processBuilder.command()} produced output: [${out.toString().trim()}]")

		if (err.toString()) {
			def message = "Running ${processBuilder.command()} produced an error: [${err.toString().trim()}]"
			println(message)
		}
	}
}

interface VcsAdapter {
	boolean isSupported(File directory)
	void commitChanges(String message)
}

class GitAdapter implements VcsAdapter {

	CliExecutor gitExecutor

	GitAdapter() {
		gitExecutor = new CliExecutor()
	}

	@Override
	boolean isSupported(File directory) {
		if (!directory.list().grep('.git')) {
			return directory.parentFile? isSupported(directory.parentFile) : false
		}
		true
	}

	@Override
	void commitChanges(String message) {
		List<String> command = ['git', 'commit', '-m', message, "-a"]
		gitExecutor.execute(new ProcessBuilder(command))
	}
}

new CrowdinCliWraper(args).execute()
