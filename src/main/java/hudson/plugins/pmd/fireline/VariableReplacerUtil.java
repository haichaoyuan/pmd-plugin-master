package hudson.plugins.pmd.fireline;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import hudson.model.TaskListener;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class VariableReplacerUtil {

    /**
     * Sets/exports shell env vars before original command, if the command contains variable name
     *
     * @param originalCommand
     * @param vars
     * @return shell script preluded with env vars
     */
    public static String preludeWithEnvVars(String originalCommand, Map<String, String> vars, TaskListener listener) {
        if (originalCommand == null) {
            return null;
        }
        if (vars == null) {
            return originalCommand;
        }

        String originalEnvVars = null;
        int beforeIndex = originalCommand.indexOf("${");
        int afterIndex = originalCommand.indexOf("}");
        if (afterIndex > beforeIndex) {
            originalEnvVars = originalCommand.substring(beforeIndex + 2, afterIndex);
        } else {
            return null;
        }

        vars.remove("_"); //why _ as key for build tool?
        for (Entry<String, String> entry : vars.entrySet()) {
            if (originalEnvVars.equalsIgnoreCase(entry.getKey())) {
                //listener.getLogger().println("key="+entry.getKey());
                //listener.getLogger().println("value="+entry.getValue());
                //listener.getLogger().println("originalEnvVars="+originalEnvVars);
                String envStr = "${" + originalEnvVars + "}";
                originalCommand = originalCommand.replace(envStr, entry.getValue());
                break;
            }
        }
        //listener.getLogger().println("originalCommand="+originalCommand);
        return originalCommand;
    }

    public static String preludeWithBuild(Run<?, ?> run, TaskListener listener, String originalString) throws IOException, InterruptedException {
        Map<String, String> vars = new HashMap<String, String>();
        if (run != null) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            vars.putAll(build.getEnvironment(listener));
            vars.putAll(build.getBuildVariables());
        }
        return VariableReplacerUtil.preludeWithEnvVars(originalString, vars, listener);
    }


    /** 当发现使用环境变量时，自动去转换对应参数，否则返回原值
     * @param run run
     * @param listener listener
     * @param originalString originalString
     * @return 当发现使用环境变量时，自动去转换对应参数，否则返回原值
     * @throws IOException IOException
     * @throws InterruptedException InterruptedException
     */
    public static String checkEnvVars(Run<?, ?> run, TaskListener listener, String originalString) throws IOException, InterruptedException {
        if (originalString.contains("${") && originalString.contains("}")) {
            return preludeWithBuild(run, listener, originalString);
        } else {
            return originalString;
        }

    }
}
