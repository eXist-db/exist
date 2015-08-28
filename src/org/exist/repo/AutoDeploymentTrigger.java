package org.exist.repo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.exist.util.FileUtils;
import org.expath.pkg.repo.*;

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
        if (property.equalsIgnoreCase("off")) {
            return;
        }

        final Optional<Path> homeDir = broker.getConfiguration().getExistHome();
        final Path autodeployDir = FileUtils.resolve(homeDir, AUTODEPLOY_DIRECTORY);
        if (!Files.isReadable(autodeployDir) && Files.isDirectory(autodeployDir)) {
            return;
        }

        try {
            final List<Path> xars = Files
                    .find(autodeployDir, 1, (path, attrs) -> (!attrs.isDirectory()) && FileUtils.fileName(path).endsWith(".xar"))
                    .sorted((o1, o2) -> o1.getFileName().compareTo(o2.getFileName()))
                    .collect(Collectors.toList());

            LOG.info("Scanning autodeploy directory. Found " + xars.size() + " app packages.");

            final Deployment deployment = new Deployment(broker);

            // build a map with uri -> file so we can resolve dependencies
            final Map<String, Path> packages = new HashMap<>();
            for (final Path xar : xars) {
                try {
                    final String name = deployment.getNameFromDescriptor(xar);
                    packages.put(name, xar);
                } catch (final IOException e) {
                    LOG.warn("Caught exception while reading app package " + xar.toAbsolutePath().toString(), e);
                } catch (final PackageException e) {
                    LOG.warn("Caught exception while reading app package " + xar.toAbsolutePath().toString(), e);
                }
            }

            final PackageLoader loader = new PackageLoader() {
                @Override
                public Path load(String name, PackageLoader.Version version) {
                    // TODO: enforce version check
                    return packages.get(name);
                }
            };

            for (final Path xar : xars) {
                try {
                    deployment.installAndDeploy(xar, loader, false);
                } catch (final PackageException e) {
                    LOG.warn("Exception during deployment of app " + FileUtils.fileName(xar) + ": " + e.getMessage(), e);
                    broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
                } catch (final IOException e) {
                    LOG.warn("Exception during deployment of app " + FileUtils.fileName(xar) + ": " + e.getMessage(), e);
                    broker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
                }
            }
        } catch(final IOException ioe) {
            LOG.error(ioe);
        }
    }


}
