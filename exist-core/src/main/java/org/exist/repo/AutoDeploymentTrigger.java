/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.repo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.XarFileSource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Startup trigger for automatic deployment of application packages. Scans the "autodeploy" directory
 * for .xar files. Installs any application which does not yet exist in the database.
 */
public class AutoDeploymentTrigger implements StartupTrigger {

    private final static Logger LOG = LogManager.getLogger(AutoDeploymentTrigger.class);

    public final static String AUTODEPLOY_DIRECTORY = "autodeploy";

    public final static String AUTODEPLOY_PROPERTY = "exist.autodeploy";
    public final static String AUTODEPLOY_DIRECTORY_PROPERTY = "exist.autodeploy.dir";
    public final static String IGNORE_AUTODEPLOY_SYSTEM_PROPERTY_PARAM = "ignore-autodeploy-system-property";
    public final static String AUTODEPLOY_DIRECTORY_PARAM = "dir";

    @Override
    public void execute(final DBBroker sysBroker, final Txn transaction, final Map<String, List<? extends Object>> params) {
        final boolean ignoreAutodeploySystemProperty = Optional.ofNullable(getFirstParamValue(params, IGNORE_AUTODEPLOY_SYSTEM_PROPERTY_PARAM, v -> Boolean.valueOf(v.toString()))).orElse(false);
        if (!ignoreAutodeploySystemProperty) {
            // do not execute if the system property exist.autodeploy=off
            final String property = System.getProperty(AUTODEPLOY_PROPERTY, "on");
            if ("off".equalsIgnoreCase(property)) {
                return;
            }
        }

        Path autodeployDir = Optional.ofNullable(System.getProperty(AUTODEPLOY_DIRECTORY_PROPERTY)).map(Paths::get).orElse(null);
        if (autodeployDir == null) {
            final String dir = getFirstParamValue(params, AUTODEPLOY_DIRECTORY_PARAM, Object::toString);
            if (dir != null) {
                autodeployDir = Paths.get(dir);
            } else {
                final Optional<Path> homeDir = sysBroker.getConfiguration().getExistHome();
                autodeployDir = FileUtils.resolve(homeDir, AUTODEPLOY_DIRECTORY);
            }
        }

        if (!Files.isReadable(autodeployDir) && Files.isDirectory(autodeployDir)) {
            LOG.warn("Unable to read autodeploy directory: {}", autodeployDir);
            return;
        }

        try (Stream<Path> xarsStream = Files
                .find(autodeployDir, 1, (path, attrs) -> (!attrs.isDirectory()) && FileUtils.fileName(path).endsWith(".xar"))
                .sorted(Comparator.comparing(Path::getFileName))) {
            
            final List<Path> xars = xarsStream.collect(Collectors.toList());

            LOG.info("Scanning autodeploy directory. Found {} app packages.", xars.size());

            final Deployment deployment = new Deployment();

            // build a map with uri -> file so we can resolve dependencies
            final Map<String, Path> packages = new HashMap<>();
            for (final Path xar : xars) {
                try {
                    final Optional<String> name = deployment.getNameFromDescriptor(sysBroker, new XarFileSource(xar));
                    if(name.isPresent()) {
                        packages.put(name.get(), xar);
                    } else {
                        LOG.error("No descriptor name for: {}", xar.toAbsolutePath().toString());
                    }
                } catch (final IOException | PackageException e) {
                    LOG.error("Caught exception while reading app package {}", xar.toAbsolutePath().toString(), e);
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
                    LOG.error("Exception during deployment of app {}: {}", FileUtils.fileName(xar), e.getMessage(), e);
                    sysBroker.getBrokerPool().reportStatus("An error occurred during app deployment: " + e.getMessage());
                }
            }
        } catch(final IOException ioe) {
            LOG.error(ioe);
        }
    }

    private @Nullable <T> T getFirstParamValue(final Map<String, List<? extends Object>> params, final String paramName, final Function<Object, T> valueConverter) {
        final List<? extends Object> values = params.get(paramName);
        if (values != null && !values.isEmpty()) {
            final Object value = values.getFirst();
            if (value != null) {
                return valueConverter.apply(value);
            }
        }
        return null;
    }
}
