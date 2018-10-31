package hudson.plugins.pmd;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.pmd.fireline.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

/**
 * Sample {@link Builder}.
 *
 * @author weihao
 */
public class FireLineBuilder extends Builder implements SimpleBuildStep {
    private final FireLineTarget fireLineTarget;
    //    private String config;
//    private String reportPath;
    private String jdk;
    private static String mJarFile = "xh-p3c-pmd-2.0.1.jar";
    private static String jarFile = "/lib/" + mJarFile;
    private static String mAliPmdFile = "ali-pmd.xml";
    private static String aliPmdFile = "/ruleset/" + mAliPmdFile;
    public final static String platform = System.getProperty("os.name");
    private FireLineScanCodeAction fireLineAction = new FireLineScanCodeAction();

    @DataBoundConstructor
    public FireLineBuilder(@Nonnull FireLineTarget fireLineTarget) {
        this.fireLineTarget = fireLineTarget;
    }

    @Nonnull
    public FireLineTarget getFireLineTarget() {
        return this.fireLineTarget;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Override
    public void perform(Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        listener.getLogger().println("[FireLineBuilder] perform...");
        if (fireLineTarget == null) {
            listener.getLogger().println("fireLineTarget is null");
        } else {
            if (fireLineTarget.getCsp()) {
                initEnv();
            }

            EnvVars env = BuilderUtils.getEnvAndBuildVars(build, listener);
            //本地项目路径
            String projectPath = workspace.getRemote();
            String reportFileNameTmp = fireLineTarget.getReportFileName().substring(0,
                    fireLineTarget.getReportFileName().lastIndexOf("."));
            String jarPath = null;
            String ruleSetPath = null;
            String cmd = null;
            String buildWithParameter = fireLineTarget.getBuildWithParameter();
            buildWithParameter = VariableReplacerUtil.checkEnvVars(build, listener, buildWithParameter);
            reportFileNameTmp = VariableReplacerUtil.checkEnvVars(build, listener, reportFileNameTmp);
//            config = fireLineTarget.getConfiguration();
//            reportPath = VariableReplacerUtil.checkEnvVars(build, listener, fireLineTarget.getReportPath());
            //listener.getLogger().println("reportPath="+reportPath);
//            boolean IsExist = FileUtils.createDir(reportPath);
//            if (!IsExist) {
//                listener.getLogger().println("结果报告路径创建失败，请确认当前Jenkins用户的权限");
//            }
            if (fireLineTarget.getCsp()) {
                listener.getLogger().println("CSP=" + System.getProperty("hudson.model.DirectoryBrowserSupport.CSP"));
            }
            jdk = fireLineTarget.getJdk();
            listener.getLogger().println("[FireLineBuilder] jdk:" + jdk);
            // add actions
            if (null != fireLineAction) {
                //change by hc:隐藏动作
//                build.addAction(fireLineAction);
            }
            // Set JDK version
            computeJdkToUse(build, workspace, listener, env);
            // get path of fireline.jar
            jarPath = getFireLineJar(listener);
            ruleSetPath = getRulesetFile(listener);
            listener.getLogger().println("[FireLineBuilder] jarPath:" + jarPath + ", ruleSetPath=" + ruleSetPath);
            // check params
            listener.getLogger().println("[FireLineBuilder] projectPath:" + projectPath);
            if (!FileUtils.existFile(projectPath)) {
                listener.getLogger().println("The path of project ：" + projectPath + "can't be found.");
            }

//            if (fireLineTarget.getJvm() != null) {
//                cmd = "java " + fireLineTarget.getJvm() + " -jar " + jarPath + " -s=" + projectPath + " -r="
//                        + reportPath + " reportFileName=" + reportFileNameTmp;
//            } else {
//                cmd = "java " + " -jar " + jarPath + " -s=" + projectPath + " -r="
//                        + reportPath + " reportFileName=" + reportFileNameTmp;
//            }
//            listener.getLogger().println("[FireLineBuilder] cmd:" + cmd);
//            if (config != null) {
//                File confFile = new File(reportPath + File.separator + "config.xml");
//                FileUtils.createXml(confFile, config);
//                if (confFile.exists() && !confFile.isDirectory()) {
//                    cmd = cmd + " config=" + confFile;
//                }
//            }

            if (buildWithParameter != null && buildWithParameter.contains("false")) {
                listener.getLogger().println("Build without FireLine !!!");
            } else {
                String pmdXmlFilePath = projectPath + "/pmd.xml";
                // debug/
                // if (checkFireLineJdk(getProject(build).getJDK())) {
                if (new File(jarPath).exists()) {
                    // execute fireline
                    listener.getLogger().println("FireLine start scanning...");
                    //listener.getLogger().println("FireLine command="+cmd);
                    //使用  pmd 进行线上扫描
//                    cmd = "java -cp"+jarPath +" net.sourceforge.pmd.PMD -d "+projectPath + " -R java-xh-comment -f html -r report/pmd.html";
//                    cmd = "java " + "" + " -jar " + jarPath + " -d " + projectPath + " -R java-xh-jenkins-block -f xml -r " + pmdXmlFilePath;
                    cmd = "java -jar " + jarPath + " -d " + projectPath + "/app/src -R " + ruleSetPath + " -f xml -r " + pmdXmlFilePath;
                    listener.getLogger().println("[FireLineBuilder] cmd:" + cmd);
                    exeCmd(cmd, listener);
                    // if block number of report is not 0,then this build is set Failure.
                    if (fireLineTarget.getBlockBuild()) {
                        int blockNum = getBlockNum(pmdXmlFilePath, listener);
                        listener.getLogger().println("[FireLineBuilder] blockNum:" + blockNum);
                        if (blockNum != 0) {
                            build.setResult(Result.FAILURE);
                            listener.getLogger().println(
                                    "[ERROR] There are some defects of \"Block\" level and set build result to FAILURE");
                        }
                    }
                    listener.getLogger().println("FireLine report path: " + pmdXmlFilePath);
                } else {
                    listener.getLogger().println(pmdXmlFilePath + " does not exist!!");
                }
            }
        }
    }

    private void initEnv() {
        System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "sandbox allow-scripts; default-src *; style-src * http://* 'unsafe-inline' 'unsafe-eval'; script-src 'self' http://* 'unsafe-inline' 'unsafe-eval'");
    }

    private void exeCmd(String commandStr, TaskListener listener) {
        Process p = null;
        try {
            Runtime rt = Runtime.getRuntime();
            // listener.getLogger().println(commandStr);
            p = rt.exec(commandStr);
            listener.getLogger().println("CommandLine output:");
            StreamGobbler errorGobbler = new StreamGobbler(p.getErrorStream(), "ERROR", listener);
            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), "INFO", listener);
            errorGobbler.start();
            outputGobbler.start();
            p.waitFor();
        } catch (RuntimeException e) {
            throw (e);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }

    private int getBlockNumOld(String reportPath, String reportFileName) {
        String xmlPath = null;
        DocumentBuilderFactory foctory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        if (reportFileName != null && reportPath != null) {
            xmlPath = reportPath + File.separator + reportFileName + ".xml";
            try {
                builder = foctory.newDocumentBuilder();
                Document doc = builder.parse(new File(xmlPath));
                NodeList nodeLists = doc.getElementsByTagName("blocknum");
                if (nodeLists != null && nodeLists.getLength() > 0) {
                    org.w3c.dom.Node node = nodeLists.item(0);
                    if (node != null) {
                        return Integer.parseInt(node.getTextContent());
                    }
                }
            } catch (ParserConfigurationException | SAXException | IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return 0;
    }

    /**
     * 判断 violation 数
     *
     * @param reportPath
     * @param listener
     * @return
     */
    private int getBlockNum(String reportPath, TaskListener listener) {
        String xmlPath = null;
        DocumentBuilderFactory foctory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder;
        int num1 = 0;
        int num2 = 0;
        int num3 = 0;
        if (reportPath != null) {
            xmlPath = reportPath;
            try {
                builder = foctory.newDocumentBuilder();
                Document doc = builder.parse(new File(xmlPath));
                NodeList nodeLists = doc.getElementsByTagName("violation");
                if (nodeLists != null && nodeLists.getLength() > 0) {
                    for (int i = 0; i < nodeLists.getLength(); i++) {
                        org.w3c.dom.Node item = nodeLists.item(i);
                        String priority = item.getAttributes().getNamedItem("priority").getNodeValue();
                        if ("1".equals(priority)) {
                            num1++;

                        } else if ("2".equals(priority)) {
                            num2++;
                        } else if ("3".equals(priority)) {
                            num3++;
                        }
                    }
                }
            } catch (ParserConfigurationException | SAXException | IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        listener.getLogger().println("[FireLineBuilder] num1:" + num1 + ",num2:" + num2 + ",num3:" + num3);
        return num1 + num2;
    }

    /**
     * 获取 Jar 包路径
     *
     * @param listener listener
     * @return 获取 Jar 包路径
     */
    public String getFireLineJar(TaskListener listener) {
        String oldPath = null;
        String newPath = null;
        if (platform.contains("Linux")) {
            oldPath = FireLineBuilder.class.getResource(jarFile).getFile();

            listener.getLogger().println("[FireLineBuilder] linux oldPayh:" + oldPath);
            if (!oldPath.contains("file:")) {
                return oldPath;
            }
            int index1 = oldPath.indexOf("file:");
            int index2 = oldPath.indexOf("lib");
            newPath = oldPath.substring(index1 + 5, index2 + 3) + File.separator + mJarFile;
        } else {
            oldPath = new File(FireLineBuilder.class.getResource(jarFile).getFile()).getAbsolutePath();
            listener.getLogger().println("[FireLineBuilder] unlinux oldPayh:" + oldPath);
            if (!oldPath.contains("file:")) {
                return oldPath;
            }
            int index1 = oldPath.indexOf("file:");
            int index2 = oldPath.indexOf("lib");
            newPath = oldPath.substring(index1 + 5, index2 + 3) + File.separator + mJarFile;
        }
        if (!new File(newPath).exists()) {
            try {
                JarCopy.copyJarResource(jarFile, newPath);
            } catch (Exception e) {
                // TODO 自动生成的 catch 块
                e.printStackTrace();
            }
        }
        return newPath;
    }

    /**
     * 获取 规则路径
     *
     * @param listener listener
     * @return 获取 规则路径
     */
    public String getRulesetFile(TaskListener listener) {
        String oldPath = null;
        String newPath = null;
        if (platform.contains("Linux")) {
            oldPath = FireLineBuilder.class.getResource(aliPmdFile).getFile();

            listener.getLogger().println("[FireLineBuilder] linux oldPayh:" + oldPath);
            if (!oldPath.contains("file:")) {
                return oldPath;
            }
            int index1 = oldPath.indexOf("file:");
            int index2 = oldPath.indexOf("lib");
            newPath = oldPath.substring(index1 + 5, index2 + 3) + File.separator + mAliPmdFile;
        } else {
            oldPath = new File(FireLineBuilder.class.getResource(aliPmdFile).getFile()).getAbsolutePath();
            listener.getLogger().println("[FireLineBuilder] unlinux oldPayh:" + oldPath);
            if (!oldPath.contains("file:")) {
                return oldPath;
            }
            int index1 = oldPath.indexOf("file:");
            int index2 = oldPath.indexOf("lib");
            newPath = oldPath.substring(index1 + 5, index2 + 3) + File.separator + mAliPmdFile;
        }
        if (!new File(newPath).exists()) {
            try {
                JarCopy.copyJarResource(aliPmdFile, newPath);
            } catch (Exception e) {
                // TODO 自动生成的 catch 块
                e.printStackTrace();
            }
        }
        return newPath;
    }

    public static String getMemUsage() {
        long free = Runtime.getRuntime().freeMemory();
        long total = Runtime.getRuntime().totalMemory();
        StringBuffer buf = new StringBuffer();
        buf.append("[Mem: used ").append((total - free) >> 20).append("M free ").append(free >> 20).append("M total ")
                .append(total >> 20).append("M]");
        return buf.toString();
    }

    private static AbstractProject<?, ?> getProject(Run<?, ?> run) {
        AbstractProject<?, ?> project = null;
        if (run instanceof AbstractBuild) {
            AbstractBuild<?, ?> build = (AbstractBuild<?, ?>) run;
            project = build.getProject();
        }
        return project;
    }

    private void computeJdkToUse(Run<?, ?> build, FilePath workspace, TaskListener listener, EnvVars env)
            throws IOException, InterruptedException {
        JDK jdkToUse = getJdkToUse(getProject(build));
        if (jdkToUse != null) {
            Computer computer = workspace.toComputer();
            // just in case we are not in a build
            if (computer != null) {
                Node node = computer.getNode();
                if (node != null) {
                    jdkToUse = jdkToUse.forNode(computer.getNode(), listener);
                }
            }
            jdkToUse.buildEnvVars(env);
        }
    }

    /**
     * @return JDK to be used with this project.
     */
    private JDK getJdkToUse(@Nullable AbstractProject<?, ?> project) {
        JDK jdkToUse = getJdkFromJenkins();
        if (jdkToUse == null && project != null) {
            jdkToUse = project.getJDK();
        }
        return jdkToUse;
    }

    /**
     * Gets the JDK that this builder is configured with, or null.
     */
    @CheckForNull
    public JDK getJdkFromJenkins() {
        Jenkins jenkins = Jenkins.getInstance();
        if (jdk != null && jenkins != null) {
            return jenkins.getJDK(jdk);
        }
        return null;
    }

    public boolean checkFireLineJdk(JDK jdkToUse) {
        String jdkPath = jdkToUse.getHome();
        return jdk != null && (jdkPath.contains("1.8") || jdkPath.contains("1.7"));
    }

    @Override
    public Action getProjectAction(AbstractProject<?, ?> project) {
        //此处 不返回 fireLineAction，则在左侧和Project结果面板不产生火图标
        return super.getProjectAction(project);
//        return fireLineAction;
    }


    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Execute Xuehai FireLine";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return super.configure(req, formData);
        }
    }
}
