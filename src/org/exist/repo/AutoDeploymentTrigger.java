package org.exist.repo;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.tui.BatchUserInteraction;

/**
 * Startup trigger for automatic deployment of application packages. Scans the "autodeploy" directory
 * for .xar files. Installs any application which does not yet exist in the database.
 */
public class AutoDeploymentTrigger implements StartupTrigger {

    private final static Logger LOG = Logger.getLogger(AutoDeploymentTrigger.class);

    public final static String AUTODEPLOY_DIRECTORY = "autodeploy";

    public final static String AUTODEPLOY_PROPERTY = "exist.autodeploy";

    @Override
    public void execute(final DBBroker broker, final Map<String, List<? extends Object>> params) {
        // do not process if the system property exist.autodeploy=off
        String property = System.getProperty(AUTODEPLOY_PROPERTY, "on");
        if (property.equalsIgnoreCase("off"))
            return;

        File homeDir = broker.getConfiguration().getExistHome();
        File autodeployDir = new File(homeDir, AUTODEPLOY_DIRECTORY);
        if (!autodeployDir.canRead() && autodeployDir.isDirectory())
            return;
        ExistRepository repo = broker.getBrokerPool().getExpathRepo();
        UserInteractionStrategy interact = new BatchUserInteraction();
        File[] xars = autodeployDir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
            return file.getName().endsWith(".xar");
            }
        });

        if (xars == null) {
            LOG.error(autodeployDir.getAbsolutePath() + " does not exist.");
            return;

        } else {
            LOG.info("Scanning autodeploy directory. Found " + xars.length + " app packages.");
        }

        Deployment deployment = new Deployment(broker);
        // build a map with uri -> file so we can resolve dependencies
        final Map<String, File> packages = new HashMap<String, File>();
        for (File xar : xars) {
            try {
                String name = deployment.getNameFromDescriptor(xar);
                packages.put(name, xar);
            } catch (IOException e) {
                LOG.warn("Caught exception while reading app package " + xar.getAbsolutePath(), e);
            } catch (PackageException e) {
                LOG.warn("Caught exception while reading app package " + xar.getAbsolutePath(), e);
            }
        }

        PackageLoader loader = new PackageLoader() {
            @Override
            public File load(String name, PackageLoader.Version version) {
                // TODO: enforce version check
                return packages.get(name);
            }
        };

        for (File xar : packages.values()) {
            try {
                deployment.installAndDeploy(xar, loader, false);
            } catch (PackageException e) {
                LOG.warn("Exception during deployment of app " + xar.getName() + ": " + e.getMessage(), e);
                broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
            } catch (IOException e) {
                LOG.warn("Exception during deployment of app " + xar.getName() + ": " + e.getMessage(), e);
                broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
            }
        }
    }


}
