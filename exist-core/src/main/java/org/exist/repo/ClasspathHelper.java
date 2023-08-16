/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2012-2015 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */
package org.exist.repo;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.ExistSystemProperties;
import org.exist.start.Classpath;
import org.exist.start.EXistClassLoader;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.deps.ProcessorDependency;

/**
 * Helper class to construct classpath for expath modules containing
 * jar files. Part of start.jar
 */
public class ClasspathHelper implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(ClasspathHelper.class);

    // if no eXist version is specified in the expath-pkg.xml, we assume it is 2.2 or older
    private final static PackageLoader.Version DEFAULT_VERSION = new PackageLoader.Version("1.4.0", "2.2.1");

    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        final ClassLoader loader = brokerPool.getClassLoader();
        if (!(loader instanceof EXistClassLoader)) {
            return;
        }
        final Classpath cp = new Classpath();
        scanPackages(brokerPool, cp);
        ((EXistClassLoader)loader).addURLs(cp);
    }

    public static void updateClasspath(BrokerPool pool, org.expath.pkg.repo.Package pkg) throws PackageException {
        final ClassLoader loader = pool.getClassLoader();
        if (!(loader instanceof EXistClassLoader))
            {return;}
        if (!isCompatible(pkg)) {
            LOG.warn("Package " + pkg.getName() + " is not compatible with this version of eXist. " +
                "To avoid conflicts, Java libraries shipping with this package are not loaded.");
            return;
        }
        final FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
        final Path packageDir = resolver.resolveResourceAsFile(".");
        final Classpath cp = new Classpath();
        try {
            scanPackageDir(pkg, cp, packageDir);
            ((EXistClassLoader)loader).addURLs(cp);
        } catch (final IOException e) {
            LOG.warn("An error occurred while updating classpath for package " + pkg.getName(), e);
        }
    }

    private static void scanPackages(BrokerPool pool, Classpath classpath) {
        try {
            final Optional<ExistRepository> repo = pool.getExpathRepo();
	    if (repo.isPresent()) {
            for (final Packages pkgs : repo.get().getParentRepo().listPackages()) {
                final Package pkg = pkgs.latest();
                if (!isCompatible(pkg)) {
                    LOG.warn("Package " + pkg.getName() + " is not compatible with this version of eXist. " +
                            "To avoid conflicts, Java libraries shipping with this package are not loaded.");
                } else {
                    try {
                        final FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
                        final Path packageDir = resolver.resolveResourceAsFile(".");
                        scanPackageDir(pkg, classpath, packageDir);
                    } catch (final IOException e) {
                        LOG.warn("An error occurred while updating classpath for package " + pkg.getName(), e);
                    }
                }
            }
	    }
        } catch (final Exception e) {
            LOG.warn("An error occurred while updating classpath for packages", e);
        }
    }

    private static boolean isCompatible(Package pkg) throws PackageException {
        // determine the eXist-db version this package is compatible with
        final Collection<ProcessorDependency> processorDeps = pkg.getProcessorDeps();
        final String procVersion = ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_PRODUCT_VERSION, "1.0");
        PackageLoader.Version processorVersion = DEFAULT_VERSION;
        for (ProcessorDependency dependency: processorDeps) {
            if (Deployment.PROCESSOR_NAME.equals(dependency.getProcessor())) {
                if (dependency.getSemver() != null) {
                    processorVersion = new PackageLoader.Version(dependency.getSemver(), true);
                } else if (dependency.getSemverMax() != null || dependency.getSemverMin() != null) {
                    processorVersion = new PackageLoader.Version(dependency.getSemverMin(), dependency.getSemverMax());
                } else if (dependency.getVersions() != null) {
                    processorVersion = new PackageLoader.Version(dependency.getVersions(), false);
                }
                break;
            }
        }
        return processorVersion.getDependencyVersion().isCompatible(procVersion);
    }

    private static void scanPackageDir(Package pkg, Classpath classpath, Path module) throws IOException {
        final Path dotExist =  module.resolve(".exist");
        if (Files.exists(dotExist)) {
            if (!Files.isDirectory(dotExist)) {
                throw new IOException("The .exist config dir is not a dir: " + dotExist);
            }

            final Path cp = dotExist.resolve("classpath.txt");
            if (Files.exists(cp)) {
                try (final BufferedReader reader = Files.newBufferedReader(cp)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Path p = Paths.get(line);
                        if (!p.isAbsolute()) {
                            final FileSystemStorage.FileSystemResolver res = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
                            p = res.resolveComponentAsFile(line);
                        }
                        p = p.normalize().toAbsolutePath();

                        if (Files.exists(p)) {
                            classpath.addComponent(p.toString());
                        } else {
                            LOG.warn("Unable to add '" + p + "' to the classpath for EXPath package: " + pkg.getName() + ", as the file does not exist!");
                        }
                    }
                }
            }
        }
    }
}
