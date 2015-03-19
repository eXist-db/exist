package org.exist.repo;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.tui.BatchUserInteraction;

/**
 * Startup trigger for automatic deployment of application packages. Scans the "autodeploy" directory
 * for .xar files. Installs any application which does not yet exist in the database.
 */
public class AutoDeploymentTrigger implements StartupTrigger {

    private final static Logger LOG = LogManager.getLogger(AutoDeploymentTrigger.class);

    public final static String AUTODEPLOY_DIRECTORY = "autodeploy";

    public final static String AUTODEPLOY_PROPERTY = "exist.autodeploy";

    @Override
    public void execute(final DBBroker broker, final Map<String, List<? extends Object>> params) {
        // do not process if the system property exist.autodeploy=off
        final String property = System.getProperty(AUTODEPLOY_PROPERTY, "on");
        if (property.equalsIgnoreCase("off"))
            {return;}

        final File homeDir = broker.getConfiguration().getExistHome();
        final File autodeployDir = new File(homeDir, AUTODEPLOY_DIRECTORY);
        if (!autodeployDir.canRead() && autodeployDir.isDirectory())
            {return;}
        final ExistRepository repo = broker.getBrokerPool().getExpathRepo();
        final UserInteractionStrategy interact = new BatchUserInteraction();
        final File[] xars = autodeployDir.listFiles(new FileFilter() {
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

        Arrays.sort(xars, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        final Deployment deployment = new Deployment(broker);
        // build a map with uri -> file so we can resolve dependencies
        final Map<String, File> packages = new HashMap<String, File>();
        for (final File xar : xars) {
            try {
                final String name = deployment.getNameFromDescriptor(xar);
                packages.put(name, xar);
            } catch (final IOException e) {
                LOG.warn("Caught exception while reading app package " + xar.getAbsolutePath(), e);
            } catch (final PackageException e) {
                LOG.warn("Caught exception while reading app package " + xar.getAbsolutePath(), e);
            }
        }

        final PackageLoader loader = new PackageLoader() {
            @Override
            public File load(String name, PackageLoader.Version version) {
                // TODO: enforce version check
                return packages.get(name);
            }
        };

        for (final File xar : xars) {
            try {
                deployment.installAndDeploy(xar, loader, false);
            } catch (final PackageException e) {
                LOG.warn("Exception during deployment of app " + xar.getName() + ": " + e.getMessage(), e);
                broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
            } catch (final IOException e) {
                LOG.warn("Exception during deployment of app " + xar.getName() + ": " + e.getMessage(), e);
                broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
            }
        }
    }


}
