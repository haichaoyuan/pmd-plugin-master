package hudson.plugins.pmd.fireline;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;

public class BuilderUtils {
	private BuilderUtils() {
	    // only static
	  }
	/**
	   * Get environment vars of the run, with all values overridden by build vars
	   */
	  public static EnvVars getEnvAndBuildVars(Run<?, ?> run, TaskListener listener) throws IOException, InterruptedException {
	    EnvVars env = run.getEnvironment(listener);
	    if (run instanceof AbstractBuild) {
	      env.overrideAll(((AbstractBuild<?, ?>) run).getBuildVariables());
	    }
	    return env;
	  }
}
