/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
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
import org.exist.storage.txn.Txn;
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
    public void execute(final DBBroker sysBroker, final Txn transaction, final Map<String, List<? extends Object>> params) {
        // do not process if the system property exist.autodeploy=off
        final String property = System.getProperty(AUTODEPLOY_PROPERTY, "on");
        if (property.equalsIgnoreCase("off")) {
            return;
        }

        final Optional<Path> homeDir = sysBroker.getConfiguration().getExistHome();
        final Path autodeployDir = FileUtils.resolve(homeDir, AUTODEPLOY_DIRECTORY);
        if (!Files.isReadable(autodeployDir) && Files.isDirectory(autodeployDir)) {
            return;
        }

        try {
            final List<Path> xars = Files
                    .find(autodeployDir, 1, (path, attrs) -> (!attrs.isDirectory()) && FileUtils.fileName(path).endsWith(".xar"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .collect(Collectors.toList());

            LOG.info("Scanning autodeploy directory. Found " + xars.size() + " app packages.");

            final Deployment deployment = new Deployment();

            // build a map with uri -> file so we can resolve dependencies
            final Map<String, Path> packages = new HashMap<>();
            for (final Path xar : xars) {
                try {
                    final Optional<String> name = deployment.getNameFromDescriptor(sysBroker, new XarFileSource(xar));
                    if(name.isPresent()) {
                        packages.put(name.get(), xar);
                    } else {
                        LOG.error("No descriptor name for: " + xar.toAbsolutePath().toString());
                    }
                } catch (final IOException | PackageException e) {
                    LOG.error("Caught exception while reading app package " + xar.toAbsolutePath().toString(), e);
                }
            }

            final PackageLoader loader = (name, version) -> {
                // TODO: enforce version check
                final Path p = packages.get(name);
                if(p == null) {
                    return null;
                }
                return new XarFileSource(p);
            };

            for (final Path xar : xars) {
                try {
                    deployment.installAndDeploy(sysBroker, transaction, new XarFileSource(xar), loader, false);
                } catch (final PackageException | IOException e) {
                    LOG.error("Exception during deployment of app " + FileUtils.fileName(xar) + ": " + e.getMessage(), e);
                    sysBroker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
                }
            }
        } catch(final IOException ioe) {
            LOG.error(ioe);
        }
    }
}
