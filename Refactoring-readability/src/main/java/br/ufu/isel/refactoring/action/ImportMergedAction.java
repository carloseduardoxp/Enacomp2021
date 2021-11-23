package br.ufu.isel.refactoring.action;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.lib.Repository;
import org.refactoringminer.api.GitHistoryRefactoringMiner;
import org.refactoringminer.api.GitService;
import org.refactoringminer.api.Refactoring;
import org.refactoringminer.api.RefactoringHandler;
import org.refactoringminer.rm1.GitHistoryRefactoringMinerImpl;
import org.refactoringminer.util.GitServiceImpl;
import org.sonar.java.ast.visitors.CognitiveComplexityVisitor;
import org.sonar.java.checks.verifier.CheckVerifier;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

import br.ufu.isel.refactoring.domain.Operation;

@Component("ImportMerged")
public class ImportMergedAction implements Action {

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
	
	

	private File csvOutput = new File("statistic/saida.csv");
	
	private PrintWriter pw = null;

	@Override
	public void execute() {
		try {
			pw = new PrintWriter(csvOutput);

			pw.println("repository;refactoring_type;className;operation;readability;understandability");

			GitService gitService = new GitServiceImpl();
			GitHistoryRefactoringMiner miner = new GitHistoryRefactoringMinerImpl();

			Repository repo = gitService.cloneIfNotExists(REPOSITORIES_DIR+"/"+REPOSITORY_NAME, REPOSITORY_LINK);
			miner.detectAll(repo, BRANCH, new RefactoringHandler() {
				@Override
				public void handle(String commitId, List<Refactoring> refactorings) {
					System.out.println("Refactorings at " + commitId);
					for (Refactoring ref : refactorings) {
						System.out.println(ref.toString());
						String refactoringType = ref.getName();

						try {
							List<br.ufu.isel.refactoring.domain.Refactoring> refactoringsBefore = getScore(
									refactoringType, ref.getSourceCodeBeforeRefactoring(), Operation.BEFORE);
							List<br.ufu.isel.refactoring.domain.Refactoring> refactoringsAfter = getScore(
									refactoringType, ref.getSourceCodeAfterRefactoring(), Operation.AFTER);

							writeFile(refactoringsBefore, pw);
							writeFile(refactoringsAfter, pw);

							pw.flush();
						} catch (Exception e) {
							System.out.println("Error on request " + e);
							e.printStackTrace();
						}

					}
				}
			});

		} catch (Exception e) {
			System.out.println("Error on request " + e);
			e.printStackTrace();
		} finally {
			if (pw != null) {
				pw.close();
			}
		}

	}

	private void writeFile(List<br.ufu.isel.refactoring.domain.Refactoring> refactorings, PrintWriter pw) {
		for (br.ufu.isel.refactoring.domain.Refactoring ref : refactorings) {
			ref.toFile(pw);
		}

	}

	private List<br.ufu.isel.refactoring.domain.Refactoring> getScore(String refactoringType,
			Map<String, String> codeMap, Operation operation) throws Exception {
		List<br.ufu.isel.refactoring.domain.Refactoring> refactorings = new ArrayList<>();
		for (String className : codeMap.keySet()) {
			br.ufu.isel.refactoring.domain.Refactoring ref = new br.ufu.isel.refactoring.domain.Refactoring();
			ref.setRepositoryUrl(REPOSITORY_LINK);
			ref.setRefactoringType(refactoringType);
			ref.setClassName(className);
			ref.setOperation(operation);

			String code = codeMap.get(className);
			String filePath = getFilePath(code, className);

			ref.setReadability(getReadabilityScore(filePath));
			ref.setUnderstandability(getUnderstandabilityScore(filePath));

			refactorings.add(ref);

		}
		deleteTemp();
		return refactorings;
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

	public Double getUnderstandabilityScore(String filePath) throws Exception {
		try {
			CheckUnderstandabilityNew check = new CheckUnderstandabilityNew();

			CheckVerifier.newVerifier().onFile(filePath).withCheck(check).withoutSemantic().verifyNoIssues();

			return check.getTotal().doubleValue();

		} catch (Exception e) {
			e.printStackTrace();
			return -1d;
		} catch (AssertionError ae) {
			return -1d;
		}
	}
}

class CheckUnderstandabilityNew extends IssuableSubscriptionVisitor {

	Integer total = 0;

	@Override
	public List<Tree.Kind> nodesToVisit() {
		return Arrays.asList(Tree.Kind.METHOD, Tree.Kind.CONSTRUCTOR);
	}

	@Override
	public void visitNode(Tree tree) {
		MethodTree method = (MethodTree) tree;
		CognitiveComplexityVisitor.Result result = CognitiveComplexityVisitor.methodComplexity(method);
		total += result.complexity;
	}

	public Integer getTotal() {
		return total;
	}

}