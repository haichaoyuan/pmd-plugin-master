package hudson.plugins.pmd;

import hudson.*;
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
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sample {@link Builder}.
 *
 * @author weihao
 */
public class FireLineBuilder extends Builder {
    private final FireLineTarget fireLineTarget;
    //    private String config;
//    private String reportPath;
    private String jdk;
    private static String mJarFile = "xh-p3c-pmd-2.0.6.jar";
    private static String jarFile = "/lib/" + mJarFile;
    private static String mAliPmdFile = "ali-pmd.xml";
    private static String aliPmdFile = "/ruleset/" + mAliPmdFile;
    public final static String platform = System.getProperty("os.name");
    private FireLineScanCodeAction fireLineAction = new FireLineScanCodeAction();
    private Pattern zhitongyunPattern = null;//检查 zhitongyun 的正则

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
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        FilePath workspace = build.getWorkspace();
        if (workspace == null) {
            throw new AbortException("no workspace for " + build);
        }
        listener.getLogger().println("[FireLineBuilder] perform...");
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
            listener.getLogger().println("[ERROR] The path of project ：" + projectPath + "can't be found.");
            return false;
        }

        //检查 build.gradle 文件是否存在没有使用 dependence 文件的情况
        boolean b = checkDependence(listener, projectPath);
        if (fireLineTarget.getDependenceCheck() && !b) {
            build.setResult(Result.FAILURE);
            listener.getLogger().println(
                    "[ERROR] The components of build.gradle using  do not come from  dependence folder and set build result to FAILURE");
            return false;
        }

        if (buildWithParameter != null && buildWithParameter.contains("false")) {
            listener.getLogger().println("Build without FireLine !!!");
        } else {
            String pmdXmlFilePath = projectPath + "/pmd.xml";
            if (new File(jarPath).exists()) {
                listener.getLogger().println("FireLine start scanning...");
                //add 1105: 查看文件下的所有 src 文件
                String destFilePath = getDestFileList(projectPath);
                //listener.getLogger().println("FireLine command="+cmd);
                //使用  pmd 进行线上扫描
//                    cmd = "java -cp"+jarPath +" net.sourceforge.pmd.PMD -d "+projectPath + " -R java-xh-comment -f html -r report/pmd.html";
//                    cmd = "java " + "" + " -jar " + jarPath + " -d " + projectPath + " -R java-xh-jenkins-block -f xml -r " + pmdXmlFilePath;
                cmd = "java -jar " + jarPath + " -d " + destFilePath + " -R " + ruleSetPath + " -f xml -r " + pmdXmlFilePath;
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
                        return false;
                    }
                }
                listener.getLogger().println("[FireLineBuilder] FireLine report path: " + pmdXmlFilePath);
            } else {
                listener.getLogger().println(pmdXmlFilePath + " does not exist!!");
            }
        }
        return true;
    }


    /**
     * 检查 Dependence 依赖
     * 1. 查找所有 build.gradle 文件
     * 2. 读取 build.gradle 文件内容
     * 3. 正则匹配，检查是否存在 "com.zhitongyun:"
     *
     * @param projectPath 项目地址
     * @return 检查通过，返回 true
     */
    private boolean checkDependence(@Nonnull TaskListener listener, @Nonnull String projectPath) {
        boolean result = true;
        File file = new File(projectPath);
        if (!file.exists()) {
            listener.getLogger().println("[ERROR] projectPath is not exist");
            return false;
        }
        //遍历文件夹,获取所有 build.gradle 文件
        File[] files = file.listFiles();
        if (files == null) {
            listener.getLogger().println("[ERROR] projectPath folder have not sub files");
            return false;
        }
        String destFileName = "build.gradle";
        ArrayList<File> destFiles = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            File fileTmp = files[i];
            //剔除文件
            if (!file.exists() || !fileTmp.isDirectory()) {
                continue;
            }
            File[] subFiles = fileTmp.listFiles();
            if (subFiles == null) {
                continue;
            }
            for (int j = 0; j < subFiles.length; j++) {
                File subFileTmp = subFiles[j];
                String subFileTmpName = subFileTmp.getName();
                if (destFileName.equals(subFileTmpName)) {
//                    listener.getLogger().println("[FireLineBuilder] subFileTmp: " + subFileTmp.getPath());
                    destFiles.add(subFileTmp);
                }
            }
        }
        listener.getLogger().println("[FireLineBuilder] destFiles: " + destFiles.toString());

        //检查所有 build.gradle 文件内容
        BufferedReader bufferedReader = null;
        String s;

        for (int i = 0; i < destFiles.size(); i++) {
            File destFile = destFiles.get(i);
            try {
                // file -> fileInputStream -> InputStreamReader -> BufferedReader
                FileInputStream fileInputStream = new FileInputStream(destFile);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                bufferedReader = new BufferedReader(inputStreamReader);
//                listener.getLogger().println("dependence.gradle:"+ destFile.getPath());
                while ((s = bufferedReader.readLine()) != null) {
//                    listener.getLogger().println("dependence.gradle:" + s);
                    if (zhitongyunPattern == null) {
                        zhitongyunPattern = Pattern.compile(".*com\\.zhitongyun:.*");
                    }
                    Matcher matcher = zhitongyunPattern.matcher(s);
                    if (matcher.find()) {
                        listener.getLogger().println("[ERROR] build.gradle:" + s);
                        result = false;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (bufferedReader != null) {
                    try {
                        bufferedReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return result;
    }

    /**
     * 获取目标文件列表，单模块的 app/src/main ,多模块的 module_name/src/main
     *
     * @param projectPath 项目路径
     * @return 获取目标文件列表
     */
    private String getDestFileList(String projectPath) {
        List<String> destFileList = new ArrayList<>();
        File file = new File(projectPath);

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (int i = 0; i < files.length; i++) {
                    File tmpFile = files[i];
                    //[1].剔除非文件夹
                    if (!tmpFile.isDirectory()) {
                        continue;
                    }
                    // [2]. 取文件夹的子文件列表
                    File[] tmpFiles2 = tmpFile.listFiles();
                    if (tmpFiles2 != null) {
                        for (int j = 0; j < tmpFiles2.length; j++) {
                            File file2 = tmpFiles2[j];
                            //[3].文件夹且名为 src 子文件夹名为 main
                            if (file2.isDirectory() && "src".equals(file2.getName())) {
                                File[] tmpFiles3 = file2.listFiles();
                                if (tmpFiles3 != null) {
                                    for (int k = 0; k < tmpFiles3.length; k++) {
                                        File file4 = tmpFiles3[k];
                                        //[3].文件夹且名为 src 子文件夹名为 main
                                        if (file4.isDirectory() && "main".equals(file4.getName())) {
                                            destFileList.add(file4.getAbsolutePath());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (destFileList.size() == 0) {
            //上述一串操作没有找到，此处用作兜底
            destFileList.add(projectPath);
        }
        //此处 list -> String
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < destFileList.size(); i++) {
            if (i != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(destFileList.get(i));
        }
        return stringBuilder.toString();
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
