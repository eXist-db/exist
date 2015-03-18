package org.exist.repo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.start.Classpath;
import org.exist.start.EXistClassLoader;
import org.exist.storage.BrokerPool;
import org.expath.pkg.repo.*;
import org.expath.pkg.repo.Package;

import java.io.*;

/**
 * Helper class to construct classpath for expath modules containing
 * jar files. Part of start.jar
 */
public class ClasspathHelper {

    private final static Logger LOG = LogManager.getLogger(ClasspathHelper.class);

    public static void updateClasspath(BrokerPool pool) {
        final ClassLoader loader = pool.getClassLoader();
        if (!(loader instanceof EXistClassLoader))
            {return;}
        final Classpath cp = new Classpath();
        scanPackages(pool, cp);
        ((EXistClassLoader)loader).addURLs(cp);
    }

    public static void updateClasspath(BrokerPool pool, org.expath.pkg.repo.Package pkg) {
        final ClassLoader loader = pool.getClassLoader();
        if (!(loader instanceof EXistClassLoader))
            {return;}
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
                try {
                    final FileSystemStorage.FileSystemResolver resolver = (FileSystemStorage.FileSystemResolver) pkg.getResolver();
                    final File packageDir = resolver.resolveResourceAsFile(".");
                    scanPackageDir(classpath, packageDir);
                } catch (final IOException e) {
                    LOG.warn("An error occurred while updating classpath for package " + pkg.getName(), e);
                }
            }
        } catch (final Exception e) {
            LOG.warn("An error occurred while updating classpath for packages", e);
        }
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
