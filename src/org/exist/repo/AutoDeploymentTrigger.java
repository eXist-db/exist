package org.exist.repo;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.tui.BatchUserInteraction;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;

/**
 * Startup trigger for automatic deployment of application packages. Scans the "autodeploy" directory
 * for .xar files. Installs any application which does not yet exist in the database.
 */
public class AutoDeploymentTrigger implements StartupTrigger {

    private final static Logger LOG = Logger.getLogger(AutoDeploymentTrigger.class);

    public final static String AUTODEPLOY_DIRECTORY = "autodeploy";

    @Override
    public void execute(DBBroker broker) {
        File homeDir = broker.getConfiguration().getExistHome();
        File autodeployDir = new File(homeDir, AUTODEPLOY_DIRECTORY);
        if (!autodeployDir.canRead() && autodeployDir.isDirectory())
            return;
        try {
            ExistRepository repo = getRepository(homeDir);
            UserInteractionStrategy interact = new BatchUserInteraction();
            File[] xars = autodeployDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File file) {
                    return file.getName().endsWith(".xar");
                }
            });
            LOG.info("Scanning autodeploy directory. Found " + xars.length + " app packages.");
            for (File xar : xars) {
                Deployment deployment = new Deployment(broker);
                try {
                    // extract name URI from .xar to compare with installed packages
                    String name = deployment.getNameFromDescriptor(xar);
                    LOG.debug("Checking package " + name);
                    Packages packages = repo.getParentRepo().getPackages(name);
                    if (packages != null) {
                        LOG.info("Found: " + packages.name());
                        LOG.info("Application package " + name + " already installed. Skipping.");
                    } else {
                        org.expath.pkg.repo.Package pkg = repo.getParentRepo().installPackage(xar, true, interact);
                        String pkgName = pkg.getName();
                        broker.getBrokerPool().reportStatus("Installing app: " + pkg.getAbbrev());
                        deployment.deploy(pkgName, repo, null);
                    }
                } catch (PackageException e) {
                    LOG.warn("Exception during deployment of app " + xar.getName() + ": " + e.getMessage(), e);
                    broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
                } catch (IOException e) {
                    LOG.warn("Exception during deployment of app " + xar.getName() + ": " + e.getMessage(), e);
                    broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
                }
            }
        } catch (EXistException e) {
            LOG.warn("Exception caught while initializing expath repository: " + e.getMessage(), e);
            broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
        }
    }

    private static ExistRepository getRepository(File home) throws EXistException {
        try {
            if (home != null){
                File repo_dir = new File(home, "webapp/WEB-INF/expathrepo");
                // ensure the dir exists
                repo_dir.mkdir();
                FileSystemStorage storage = new FileSystemStorage(repo_dir);
                return new ExistRepository(storage);
            } else {
                File repo_dir = new File(System.getProperty("java.io.tmpdir") + "/expathrepo");
                // ensure the dir exists
                repo_dir.mkdir();
                FileSystemStorage storage = new FileSystemStorage(repo_dir);
                return new ExistRepository(storage);
            }
        }
        catch ( PackageException ex ) {
            // problem with pkg-repo.jar throwing exception
            throw new EXistException("Problem setting expath repository", ex);
        }
    }


}
