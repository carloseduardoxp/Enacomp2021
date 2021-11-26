package br.ufu.isel.refactoring;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import br.ufu.isel.refactoring.action.Action;
import br.ufu.isel.refactoring.action.ImportMergedAction;
import br.ufu.isel.refactoring.action.PullRequestAction;

@Component
public class Principal {
	
	@Autowired
	protected ApplicationContext appContext;
	
	@Value("${ACTION}")
	private String action;
	
	@PostConstruct
	public void init() throws Exception {
		Action objectAction = (Action)appContext.getBean(action);
		objectAction.execute();
		
		System.out.println("Now shutting down " + action);
		int exitCode = SpringApplication.exit(appContext, (ExitCodeGenerator) () -> 0);
		System.exit(exitCode);
	}

}