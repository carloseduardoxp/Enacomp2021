package br.ufu.isel.refactoring.action;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Properties;

import org.eclipse.jgit.lib.Repository;
import org.kohsuke.github.GHIssueComment;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("PullRequestRefactoring")
public class PullRequestRefactoringAction implements Action {

	private File csvOutput = new File("statistic/pullRequestRefactoring.csv");

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

			pw.println("repository;pullRequest;refactoring");

			GitHub gitHub = connectToGitHub();
			GitService gitService = new GitServiceImpl();
			GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();
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
					//if (allComments.contains("refactoring")) {
						miner.detectAtCommit(repo, pr.getHead().getCommit().getSHA1(), new RefactoringHandler() {
							@Override
							public void handle(String commitId, List<Refactoring> refactorings) {
								System.out.println("Refactorings at " + commitId);								
								for (Refactoring ref : refactorings) {
									System.out.println(ref.toString());
									pw.println(REPOSITORY_LINK+";"+pr.getNumber()+";"+ref.getName());
									pw.flush();
								}
							}
						});
					//}
				}
			}
			pw.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e);
		}

	}

	private static GitHub connectToGitHub() {
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

}
