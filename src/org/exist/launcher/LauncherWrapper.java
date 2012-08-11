package org.exist.launcher;

import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Commandline;
import org.apache.tools.ant.types.Path;
import org.exist.util.ConfigurationHelper;

import java.io.*;

/**
 * A wrapper to call {@link Launcher} with correct VM settings.
 * Spawns a new Java process using Ant. Mainly used when launching
 * eXist by double clicking on start.jar.
 *
 * @author Tobi Krebs
 * @author Wolfgang Meier
 */
public class LauncherWrapper {

    public final static void main(String[] args) {
        LauncherWrapper wrapper = new LauncherWrapper();
        wrapper.startServer();
    }

    private void startServer() {
        Project project = new Project();

        DefaultLogger logger = new DefaultLogger();
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        logger.setMessageOutputLevel(Project.MSG_INFO);
        project.addBuildListener(logger);

        Java java = new Java();
        java.setFork(true);
        java.setSpawn(true);
        java.setClassname(org.exist.start.Main.class.getName());
        java.setProject(project);
        java.setClasspath(Path.systemClasspath);

        Commandline.Argument jvmArgs = java.createJvmarg();
        String javaOpts = getJavaOpts();
        jvmArgs.setLine(javaOpts);
        System.out.println("Java opts: " + javaOpts);

        Commandline.Argument args = java.createArg();
        args.setLine(org.exist.launcher.Launcher.class.getName());

        java.init();
        java.executeJava();
    }

    protected String getJavaOpts() {
        String home = System.getProperty("exist.home", ".");
        StringBuilder opts = new StringBuilder();

        opts.append(getVMOpts());

        opts.append(" ");
        opts.append("-Dexist.home=");
        opts.append(home);

        opts.append(" ");
        opts.append("-Djava.endorsed.dirs=");
        opts.append(home + "/lib/endorsed");

        return opts.toString();
    }

    protected String getVMOpts() {
        StringBuilder opts = new StringBuilder();
        InputStream is = null;
        File propFile = ConfigurationHelper.lookup("vm.properties");
        try {
            if (propFile.canRead()) {
                is = new FileInputStream(propFile);
            }
            if (is == null) {
                is = LauncherWrapper.class.getResourceAsStream("vm.properties");
            }
            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.matches("^\\s*#.*"))
                        opts.append(' ').append(line);
                }
                is.close();
            }
        } catch (IOException e) {
            System.err.println("vm.properties not found");
        }
        return opts.toString();
    }
}
