package br.ufu.isel.refactoring.action;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.refactoringminer.api.GitService;
import org.refactoringminer.util.GitServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.ufu.isel.refactoring.domain.Operation;

@Component("PullRequest")
public class PullRequestAction implements Action {

	private File csvOutput = new File("statistic/pullRequest.csv");

	private PrintWriter pw = null;

	@Value("${TEMP_FOLDER}")
	public String TEMP_FOLDER;

	@Value("${REPOSITORY_LINK}")
	private String REPOSITORY_LINK;

	@Value("${REPOSITORY_NAME}")
	private String REPOSITORY_NAME;

	@Value("${REPOSITORIES_DIR}")
	private String REPOSITORIES_DIR;

	@Value("${BRANCH}")
	private String BRANCH;

	@Value("${READABILITY_DIR}")
	private String READABILITY_DIR;

	@Override
	public void execute() {

		try {

			pw = new PrintWriter(csvOutput);

			pw.println("repository;pullRequest;operation;readability");

			GitHub gitHub = connectToGitHub();
			GitService gitService = new GitServiceImpl();
			Repository repo = gitService.cloneIfNotExists(REPOSITORIES_DIR + "/" + REPOSITORY_NAME, REPOSITORY_LINK);
			GHRepository repository = gitHub.getRepository("apache/" + REPOSITORY_NAME);
			List<GHPullRequest> pullRequests = repository.getPullRequests(GHIssueState.CLOSED);
			for (GHPullRequest pr : pullRequests) {
				if (pr.isMerged()) {
					List<GHIssueComment> comments = pr.getComments();
					String allComments = "";
					for (GHIssueComment c : comments) {
						allComments += c.getBody().toLowerCase() + " ";
					}
					if (allComments.contains("readability") || allComments.contains("compreensibility")
							|| allComments.contains("readable") || allComments.contains("understandability")) {
						System.out.println("Detectado no pull request "+pr.getNumber());
						detectAtCommit(repo, pr.getHead().getCommit().getSHA1(),pr.getNumber());
					}
				}
			}
			pw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e);
		}

	}

	private GitHub connectToGitHub() {
		GitHub gitHub = null;
		try {
			Properties prop = new Properties();
			InputStream input = new FileInputStream("github-oauth.properties");
			prop.load(input);
			String oAuthToken = prop.getProperty("OAuthToken");
			if (oAuthToken != null) {
				gitHub = GitHub.connectUsingOAuth(oAuthToken);
				if (gitHub.isCredentialValid()) {
					System.out.println("Connected to GitHub with OAuth token");
				}
			} else {
				gitHub = GitHub.connect();
			}
		} catch (FileNotFoundException e) {
			System.out.println(
					"File github-oauth.properties was not found in RefactoringMiner's execution directory" + e);
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return gitHub;
	}

	public void detectAtCommit(Repository repository, String commitId,Integer prNumber) throws Exception {
		File metadataFolder = repository.getDirectory();
		File projectFolder = metadataFolder.getParentFile();
		GitService gitService = new GitServiceImpl();
		RevWalk walk = new RevWalk(repository);
		RevCommit commit = walk.parseCommit(repository.resolve(commitId));
		if (commit.getParentCount() > 0) {
			walk.parseCommit(commit.getParent(0));
			populateFiles(gitService, repository, projectFolder, commit,prNumber);
		}
		walk.close();
	}

	protected void populateFiles(GitService gitService, Repository repository, File projectFolder,
			RevCommit currentCommit,Integer prNumber) throws Exception {
		List<String> filePathsBefore = new ArrayList<String>();
		List<String> filePathsCurrent = new ArrayList<String>();
		Map<String, String> renamedFilesHint = new HashMap<String, String>();
		gitService.fileTreeDiff(repository, currentCommit, filePathsBefore, filePathsCurrent, renamedFilesHint);

		Set<String> repositoryDirectoriesBefore = new LinkedHashSet<String>();
		Set<String> repositoryDirectoriesCurrent = new LinkedHashSet<String>();
		Map<String, String> fileContentsBefore = new LinkedHashMap<String, String>();
		Map<String, String> fileContentsCurrent = new LinkedHashMap<String, String>();
		try (RevWalk walk = new RevWalk(repository)) {
			if (!filePathsBefore.isEmpty() && !filePathsCurrent.isEmpty() && currentCommit.getParentCount() > 0) {
				RevCommit parentCommit = currentCommit.getParent(0);
				populateFileContents(repository, parentCommit, filePathsBefore, fileContentsBefore,
						repositoryDirectoriesBefore, Operation.BEFORE,prNumber);
				populateFileContents(repository, currentCommit, filePathsCurrent, fileContentsCurrent,
						repositoryDirectoriesCurrent, Operation.AFTER,prNumber);
			}
			walk.dispose();
		}
	}

	private void populateFileContents(Repository repository, RevCommit commit, List<String> filePaths,
			Map<String, String> fileContents, Set<String> repositoryDirectories, Operation operation,Integer prNumber) throws Exception {
		RevTree parentTree = commit.getTree();
		deleteTemp();
		try (TreeWalk treeWalk = new TreeWalk(repository)) {
			treeWalk.addTree(parentTree);
			treeWalk.setRecursive(true);
			while (treeWalk.next()) {
				String pathString = treeWalk.getPathString();
				if (filePaths.contains(pathString)) {
					ObjectId objectId = treeWalk.getObjectId(0);
					ObjectLoader loader = repository.open(objectId);
					StringWriter writer = new StringWriter();
					IOUtils.copy(loader.openStream(), writer);
					if (pathString.endsWith(".java")) {
						pathString = pathString.replaceAll("/", ".");
						String code = writer.toString();
						String filePath = getFilePath(code, pathString);

						Double readabilityScore = getReadabilityScore(filePath);
						pw.println(REPOSITORY_NAME+";"+prNumber+";"+readabilityScore + ";" + operation);
						pw.flush();
						deleteTemp();
					}
				}
			}
		}
	}

	private void deleteTemp() {
		String filePath = TEMP_FOLDER;
		File dir = new File(filePath);
		for (File file : dir.listFiles()) {
			file.delete();
		}
	}

	public String getFilePath(String code, String className) throws Exception {
		PrintWriter pw = null;
		String filePath = TEMP_FOLDER + "/" + className;
		File file = new File(filePath);
		if (!file.exists()) {
			pw = new PrintWriter(file);
			pw.println(code);
		}
		pw.close();
		return filePath;
	}

	public Double getReadabilityScore(String filePath) throws Exception {
		try {
			Path currentRelativePath = Paths.get("");

			Process p = runProcess(filePath);
			return readScore(p);
		} catch (Exception e) {
			e.printStackTrace();
			return -1d;
		} catch (Error e1) {
			return -1d;
		}
	}

	private Process runProcess(String snippetDir) throws Exception {
		File dir = new File(READABILITY_DIR);
		String cmd = "java -jar rsm.jar " + snippetDir;
		Process p = Runtime.getRuntime().exec(cmd, null, dir);
		p.waitFor();
		return p;
	}

	private Double readScore(Process process) throws Exception {
		BufferedReader bw = new BufferedReader(new InputStreamReader(process.getInputStream()));
		while (bw.ready()) {
			String line = bw.readLine();
			String[] data = line.split("	");
			if (data.length > 1 && isDouble(data[1])) {
				return Double.parseDouble(data[1]);
			}
		}
		bw.close();
		throw new Exception("cant find score ");
	}

	public static boolean isDouble(String string) {
		try {
			Double.parseDouble(string);
			return true;
		} catch (NumberFormatException nfe) {
			return false;
		}
	}

}