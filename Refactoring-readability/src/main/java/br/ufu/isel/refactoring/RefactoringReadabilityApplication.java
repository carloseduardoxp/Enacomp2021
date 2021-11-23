package br.ufu.isel.refactoring;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class })
public class RefactoringReadabilityApplication extends SpringBootServletInitializer {
	
	public static void main(String[] args) {
		new RefactoringReadabilityApplication().configure(new SpringApplicationBuilder(RefactoringReadabilityApplication.class)).run(args);
	}
	
	@Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(RefactoringReadabilityApplication.class);
    }

}
