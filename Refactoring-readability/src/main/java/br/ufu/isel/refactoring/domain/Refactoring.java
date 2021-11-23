package br.ufu.isel.refactoring.domain;

import java.io.PrintWriter;

public class Refactoring {
	
	private String repositoryUrl;
	
	private String refactoringType;
	
	private String className;
	
	private Operation operation;
	
	private Double readability;
	
	private Double understandability;
	
	public void toFile(PrintWriter pw) {
		pw.println(repositoryUrl+";"+refactoringType+";"+className+";"+operation+";"+readability+";"+understandability);
	}

	public String getRepositoryUrl() {
		return repositoryUrl;
	}

	public void setRepositoryUrl(String repositoryUrl) {
		this.repositoryUrl = repositoryUrl;
	}

	public String getRefactoringType() {
		return refactoringType;
	}

	public void setRefactoringType(String refactoringType) {
		this.refactoringType = refactoringType;
	}

	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}

	public Operation getOperation() {
		return operation;
	}

	public void setOperation(Operation operation) {
		this.operation = operation;
	}

	public Double getReadability() {
		return readability;
	}

	public void setReadability(Double readability) {
		this.readability = readability;
	}

	public Double getUnderstandability() {
		return understandability;
	}

	public void setUnderstandability(Double understandability) {
		this.understandability = understandability;
	}
	
}

