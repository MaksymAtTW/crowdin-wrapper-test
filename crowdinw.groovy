#!/usr/bin/env groovy

import groovy.text.GStringTemplateEngine
import groovy.text.Template

import java.util.regex.Matcher

/**
 * @author Maksym Bryzhko
 * @version 1.1
 */

class CrowdinCliWraper {
	static String CROWDIN_LIB_VERSION = "0.5.1"
	static String NO_BRANCH = "#no-branch#"
	static String PSEUDO_LANG = "ko"

	OptionAccessor opts
	CliBuilder cliBuilder
	// where to the Crowdin Wrapper script to run from
	String wrapperWorkingDir
	// base dir for the messages sync
	String projectWorkingDir
	String crowdinProjectApiKey
	CliExecutor crowdinCliToolExecuter
	String branch
	Boolean deletePseudoLang

	CrowdinCliWraper(String[] args) {
		this.wrapperWorkingDir = retrieveWrapperWorkingDir()
		this.crowdinProjectApiKey = retrieveApiKey()
		this.crowdinCliToolExecuter = new CliExecutor()
		this.opts = parseCommandLineOptions(args)
		this.branch = getBranchFromCommandLine(opts)
		this.deletePseudoLang = isDeletePseudoLang(opts)
		this.projectWorkingDir = retrieveProjectWorkingDir(opts)
		println("Using Crowdin wrapper working dir ${this.wrapperWorkingDir}")
	}

	private Boolean isDeletePseudoLang(OptionAccessor opt) {
		opts.getProperty("delete-pseudo-translations")
	}

	private retrieveApiKey() {
		System.getenv("CROWDINW_PROJECT_API_KEY")
	}

	private String retrieveWrapperWorkingDir() {
		def crowdinwHome = System.getenv("CROWDINW_HOME")
		def workingDirPath = crowdinwHome ?: "."
		def workingDir = new File(workingDirPath)
		if (!workingDir.exists()) throw new RuntimeException("Corwdin home dir does not exists: $workingDir")

		return workingDir.canonicalPath
	}

	private String retrieveProjectWorkingDir(OptionAccessor opts) {
		def projectWorkingDir = opts.getProperty("project-working-dir")
		if (!projectWorkingDir) return null

		def projectWorkingDirFile = new File(projectWorkingDir)
		if (!projectWorkingDirFile.exists()) throw new RuntimeException("Project working dir does not exists: $projectWorkingDirFile")

		return projectWorkingDirFile.canonicalPath
	}

	private OptionAccessor parseCommandLineOptions(String[] args) {
		cliBuilder = new CliBuilder(usage: "crowdinw.groovy [global options] command [command options] [arguments...]")
		cliBuilder.c(longOpt:'config', args:1, argName:'crowdinConfig', 'Project-specific configuration file')
		cliBuilder.t(longOpt:'conf-template', args:1, argName:'crowdinConfigTemplate', 'Template of project-specific configuration file')
		cliBuilder._(longOpt:'project-working-dir', args:1, argName:'projectWorkingDir', 'Path to the project working directory')
		cliBuilder._(longOpt:'commit', 'Commit translation into repo')
		cliBuilder._(longOpt:'delete-pseudo-translations', 'Delete downloaded translation files with pseudo language')
		cliBuilder.b(argName:'branch', 'Branch name')
		return cliBuilder.parse(args)
	}

	def execute() {
		if (!opts.arguments()) {
			cliBuilder.usage()
		} else {
			def output = crowdinCliToolExecuter.executeAndReturnOutput(crowdinCliToolProcessBuilder(opts), [errorPatterns: ["error: "]])
			def extractFiles = new UpdatedTranslationFilesParser(output, PSEUDO_LANG, deletePseudoLang)
			deletePseudoTranslations(opts, extractFiles)
			commitTranslationIfNeeded(opts, extractFiles)
		}
	}

	private def deletePseudoTranslations(OptionAccessor opts, UpdatedTranslationFilesParser files) {
		if(this.deletePseudoLang) {
			files.translationFilesForPseudoLang.each { String relativeFilePath ->
				def file = new File(wrapperWorkingDir, relativeFilePath)
				if (!file.delete()) {
					throw new RuntimeException("Cannot delete pseudo local translation file: ${file}")
				}
			}
		}
	}

	private def commitTranslationIfNeeded(OptionAccessor opts, CommitFileParameter files) {
		if(opts.getProperty("commit")) {
			def vcsAdapter = createVcsAdapter()
			vcsAdapter.commitChanges("Crowdin Sync", files)
		}
	}

	private VcsAdapter createVcsAdapter() {
		def vcsAdapter = new GitAdapter(workingDir: new File(wrapperWorkingDir), branch: (branch == NO_BRANCH ? "master" : branch))
		if (!vcsAdapter.isSupported(new File(wrapperWorkingDir))) {
			throw new RuntimeException("Git Repo did not found in working dir: ${wrapperWorkingDir}")
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
			["-c", "${wrapperWorkingDir}/crowdin/crowdin.yaml".toString()]
		}
	}

	/**
	 * Create config from template and return path to the config.
	 */
	private String pathToconfigFromTemplate(OptionAccessor opts) {
		def templateFile = new File(opts.getProperty("t").toString())

		Template template = new GStringTemplateEngine().createTemplate(templateFile)

		def projectWorkingDirToUse = projectWorkingDir ?: wrapperWorkingDir
		println("Using Project working dir ${projectWorkingDirToUse}")

		File crowdinConfigFile = File.createTempFile("crowdin", "yaml")
		crowdinConfigFile << template.make([basePath: projectWorkingDirToUse, apiKey: crowdinProjectApiKey])
		crowdinConfigFile.absolutePath
	}

	private List<String> argumentsFromCommandLine(OptionAccessor opts) {
		opts.arguments()
	}

	private ArrayList<String> baseCrowdinExecCommand() {
		["java", "-jar", "${wrapperWorkingDir}/crowdin/lib/${CROWDIN_LIB_VERSION}/crowdin-cli.jar".toString()]
	}

}

class UpdatedTranslationFilesParser implements CommitFileParameter {

	private String crowdinCliToolOutput
	private String pseudoLang
	private Boolean deletePseudoLang

	private final String[] extactedFiles

	UpdatedTranslationFilesParser(crowdinCliToolOutput, pseudoLang, deletePseudoLang) {
		this.crowdinCliToolOutput = crowdinCliToolOutput
		this.pseudoLang = pseudoLang
		this.extactedFiles = parseExtractFiles()
		this.deletePseudoLang = deletePseudoLang
	}

	private String[] parseExtractFiles() {
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

	@Override
	String[] getParameters() {
		deletePseudoLang ? extactedFiles.findAll { !isPseudoLangTranslation(it)  } : extactedFiles
	}

	String[] getTranslationFilesForPseudoLang() {
		extactedFiles.findAll { isPseudoLangTranslation(it) }
	}

	private boolean isPseudoLangTranslation(String fileNamePath) {
		fileNamePath.matches("^.*${pseudoLang}\\..*\$")
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
