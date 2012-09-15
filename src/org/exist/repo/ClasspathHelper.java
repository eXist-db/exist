package org.exist.repo;

import org.exist.start.Classpath;
import org.exist.start.EXistClassLoader;
import org.exist.storage.BrokerPool;

import java.io.*;

/**
 * Helper class to construct classpath for expath modules containing
 * jar files. Part of start.jar
 */
public class ClasspathHelper {

    public static void updateClasspath(BrokerPool pool) {
        ClassLoader loader = pool.getClassLoader();
        if (!(loader instanceof EXistClassLoader))
            return;
        File existHome = pool.getConfiguration().getExistHome();
        Classpath cp = new Classpath();
        scanPackages(cp, existHome);
        ((EXistClassLoader)loader).addURLs(cp);
    }

    private static void scanPackages(Classpath classpath, File existHome) {
        try {
            File repo;
            if (existHome != null) {
                new File(existHome, ExistRepository.EXPATH_REPO_DEFAULT).mkdir();
                repo = new File(existHome, ExistRepository.EXPATH_REPO_DEFAULT);

            } else {
                new File(System.getProperty("java.io.tmpdir") + ExistRepository.EXPATH_REPO_DIR).mkdir();
                repo = new File(System.getProperty("java.io.tmpdir") + ExistRepository.EXPATH_REPO_DIR);
            }

            File[] modules = repo.listFiles(new FileFilter() {

                public boolean accept(File file) {
                    return file.isDirectory() && !file.getName().startsWith(".");
                }
            });

            if (modules != null) {
                for (File module : modules) {
                    File exist = new File(module, ".exist");
                    if (exist.exists()) {
                        if (!exist.isDirectory()) {
                            throw new IOException("The .exist config dir is not a dir: " + exist);
                        }

                        File cp = new File(exist, "classpath.txt");
                        if (cp.exists()) {
                            BufferedReader reader = new BufferedReader(new FileReader(cp));
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
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
