package org.exist.repo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.SystemProperties;
import org.exist.start.Classpath;
import org.exist.start.EXistClassLoader;
import org.exist.storage.BrokerPool;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.deps.ProcessorDependency;

import java.io.*;
import java.util.Collection;

/**
 * Helper class to construct classpath for expath modules containing
 * jar files. Part of start.jar
 */
public class ClasspathHelper {

    private final static Logger LOG = LogManager.getLogger(ClasspathHelper.class);

    // if no eXist version is specified in the expath-pkg.xml, we assume it is 2.2 or older
    private final static PackageLoader.Version DEFAULT_VERSION = new PackageLoader.Version("1.4.0", "2.2.1");

    public static void updateClasspath(BrokerPool pool) {
        final ClassLoader loader = pool.getClassLoader();
        if (!(loader instanceof EXistClassLoader))
            {return;}
        final Classpath cp = new Classpath();
        scanPackages(pool, cp);
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
        final File packageDir = resolver.resolveResourceAsFile(".");
        final Classpath cp = new Classpath();
        try {
            scanPackageDir(cp, packageDir);
            ((EXistClassLoader)loader).addURLs(cp);
        } catch (final IOException e) {
            LOG.warn("An error occurred while updating classpath for package " + pkg.getName(), e);
        }
    }

    private static void scanPackages(BrokerPool pool, Classpath classpath) {
        try {
            final ExistRepository repo = pool.getExpathRepo();
            for (final Packages pkgs : repo.getParentRepo().listPackages()) {
                final Package pkg = pkgs.latest();
                if (!isCompatible(pkg)) {
                    LOG.warn("Package " + pkg.getName() + " is not compatible with this version of eXist. " +
                            "To avoid conflicts, Java libraries shipping with this package are not loaded.");
                } else {
                    try {
                        final FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
                        final File packageDir = resolver.resolveResourceAsFile(".");
                        scanPackageDir(classpath, packageDir);
                    } catch (final IOException e) {
                        LOG.warn("An error occurred while updating classpath for package " + pkg.getName(), e);
                    }
                }
            }
        } catch (final Exception e) {
            LOG.warn("An error occurred while updating classpath for packages", e);
        }
    }

    private static boolean isCompatible(Package pkg) throws PackageException {
        // determine the eXistdb version this package is compatible with
        final Collection<ProcessorDependency> processorDeps = pkg.getProcessorDeps();
        final String procVersion = SystemProperties.getInstance().getSystemProperty("product-semver", "1.0");
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

    private static void scanPackageDir(Classpath classpath, File module) throws IOException {
        final File exist = new File(module, ".exist");
        if (exist.exists()) {
            if (!exist.isDirectory()) {
                throw new IOException("The .exist config dir is not a dir: " + exist);
            }

            final File cp = new File(exist, "classpath.txt");
            if (cp.exists()) {
                final BufferedReader reader = new BufferedReader(new FileReader(cp));
                try {
                    String line;
                    while((line = reader.readLine()) != null) {
                        classpath.addComponent(line);
                    }
                } finally {
                    reader.close();
                }
            }
        }
    }
}
