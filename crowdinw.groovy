#!/usr/bin/env groovy

import groovy.text.GStringTemplateEngine
import groovy.text.Template

import java.util.regex.Matcher

/**
 * @author Maksym Bryzhko
 * @version 0.1
 */

class CrowdinCliWraper {
	static String CROWDIN_LIB_VERSION = "0.5.1"
	static String NO_BRANCH = "#no-branch#"
	OptionAccessor opts
	CliBuilder cliBuilder
	String workingDir
	String crowdinProjectApiKey
	CliExecutor crowdinCliToolExecuter
	String branch

	CrowdinCliWraper(String[] args) {
		this.workingDir = retrieveWorkingDir()
		this.crowdinProjectApiKey = retrieveApiKey()
		this.crowdinCliToolExecuter = new CliExecutor()
		this.opts = parseCommandLineOptions(args)
		this.branch = getBranchFromCommandLine(opts)

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

	private OptionAccessor parseCommandLineOptions(String[] args) {
		cliBuilder = new CliBuilder(usage: "crowdinw.groovy [global options] command [command options] [arguments...]")
		cliBuilder.c(longOpt:'config', args:1, argName:'crowdinConfig', 'Project-specific configuration file')
		cliBuilder.t(longOpt:'conf-template', args:1, argName:'crowdinConfigTemplate', 'Template of project-specific configuration file')
		cliBuilder._(longOpt:'commit', 'Commit translation into repo')
		cliBuilder.b(argName:'branch', 'Branch name')
		return cliBuilder.parse(args)
	}

	def execute() {
		if (!opts.arguments()) {
			cliBuilder.usage()
		} else {
			def output = crowdinCliToolExecuter.executeAndReturnOutput(crowdinCliToolProcessBuilder(opts), [errorPatterns: ["error: "]])
			commitTranslationIfNeeded(opts, output)
		}
	}

	private def commitTranslationIfNeeded(OptionAccessor opts, String crowdinCliToolOutput) {
		if(opts.getProperty("commit")) {
			def vcsAdapter = createVcsAdapter()
			vcsAdapter.commitChanges("Crowdin Sync", new UpdatedTranslationFilesParser(crowdinCliToolOutput: crowdinCliToolOutput, baseDir: workingDir))
		}
	}

	private VcsAdapter createVcsAdapter() {
		def vcsAdapter = new GitAdapter(workingDir: new File(workingDir), branch: (branch == NO_BRANCH ? "master" : branch))
		if (!vcsAdapter.isSupported(new File(workingDir))) {
			throw new RuntimeException("Git Repo did not found")
		}
		return vcsAdapter
	}

	private ProcessBuilder crowdinCliToolProcessBuilder(OptionAccessor opts) {
		List<String> crowdinCliToolRunCommand = baseCrowdinExecCommand()
		crowdinCliToolRunCommand.addAll(configLocation(opts))
		crowdinCliToolRunCommand.addAll(argumentsFromCommandLine(opts))
		crowdinCliToolRunCommand.addAll(branch())

		new ProcessBuilder(crowdinCliToolRunCommand)
	}

	private List<String> branch() {
		branch == NO_BRANCH ? [] : ['-b', branch]
	}

	private String getBranchFromCommandLine(OptionAccessor opts) {
		opts.getProperty("b") ?: NO_BRANCH
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

class UpdatedTranslationFilesParser implements CommitFileParameter {

	private String crowdinCliToolOutput
	private String baseDir

	@Override
	String[] getParameters() {

		Matcher matcher = crowdinCliToolOutput =~ ~/(?:Extracting: `(.+)')/

		String[] result = []
		if (matcher.count > 0) {
			result = (0..matcher.count - 1).collect { idx ->
				def extractedFile = matcher[idx][1] as String
				extractedFile.startsWith("/") ? extractedFile.substring(1) : extractedFile
			}
		}
		result
	}
}

class CliExecutor {
	public String executeAndReturnOutput(ProcessBuilder processBuilder, Map options = [:]) throws RuntimeException {
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

		if (options['errorPatterns'] && [out, err]*.toString().any { String s -> (options['errorPatterns'] as List<String>).any { s.contains(it) } }) {
			throw new RuntimeException("${ options['errorMessage'] ? options['errorMessage'] as String : 'Failed to run [' + processBuilder.command().join(' ') + ']' } - [$out][$err]")
		}

		return out.toString()
	}
}

interface CommitFileParameter {
	String[] getParameters()
}

interface VcsAdapter {
	boolean isSupported(File directory)
	void commitChanges(String message, CommitFileParameter fileToCommitParam)
}

class GitAdapter implements VcsAdapter {

	private static final String UNCOMMITTED = 'uncommitted'
	private static final String UNVERSIONED = 'unversioned'
	private static final String AHEAD = 'ahead'
	private static final String BEHIND = 'behind'

	CliExecutor gitExecutor
	File workingDir
	String branch

	GitAdapter() {
		gitExecutor = new CliExecutor()
	}

	private Map<String, List<String>> gitStatus() {
		gitExecutor.executeAndReturnOutput(new ProcessBuilder(['git', 'status', '--porcelain']).directory(workingDir)).readLines().groupBy {
			if (it ==~ /^\s*\?{2}.*/) {
				UNVERSIONED
			} else {
				UNCOMMITTED
			}
		}
	}


	@Override
	boolean isSupported(File directory) {
		if (!directory.list().grep('.git')) {
			return directory.parentFile? isSupported(directory.parentFile) : false
		}
		true
	}

	@Override
	void commitChanges(String message, CommitFileParameter fileToCommitParam) {
		if (gitStatus().size() > 0) {
			gitExecutor.executeAndReturnOutput(new ProcessBuilder(['git', 'add', fileToCommitParam.parameters].flatten()).directory(workingDir))
			gitExecutor.executeAndReturnOutput(new ProcessBuilder(['git', 'commit', '-m', message, fileToCommitParam.parameters].flatten()).directory(workingDir))
			gitExecutor.executeAndReturnOutput(new ProcessBuilder(['git', 'push', '--porcelain', 'origin', branch]).directory(workingDir), [errorPatterns: ['[rejected]', 'error: ', 'fatal: ']])
		} else {
			println("Nothing to commit")
		}
	}
}

new CrowdinCliWraper(args).execute()
