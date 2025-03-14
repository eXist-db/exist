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
package org.exist.webstart;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.start.LatestFileResolver;
import org.exist.util.FileUtils;

/**
 * Class for managing webstart jar files.
 *
 * @author Dannes Wessels
 */
public class JnlpJarFiles {

    private static final Logger LOGGER = LogManager.getLogger(JnlpJarFiles.class);

    private final Map<String, Path> allFiles = new HashMap<>();
    private final Path mainJar;

    // Names of core jar files sans ".jar" extension.
    // Use %latest% token in place of a version string.
    private final String allJarNames[] = new String[]{
            "antlr-%latest%",
            "cglib-nodep-%latest%",
            "clj-ds-%latest%",
            "commons-codec-%latest%",
            "commons-collections-%latest%",
            "commons-io-%latest%",
            "commons-logging-%latest%",
            "commons-pool-%latest%",
            "jargo-%latest%",
            "bcprov-jdk18on-%latest%",
            "fastutil-%latest%-min",
            "j8fu-%latest%",
            "jackson-core-%latest%",
            "jcip-annotations-%latest%",
            "jline-%latest%",
            "jansi-%latest%",
            "jta-%latest%",
            "log4j-api-%latest%",
            "log4j-core-%latest%",
            "log4j-jul-%latest%",
            "log4j-slf4j-impl-%latest%",
            "pkg-java-%latest%",
            "quartz-%latest%",
            "rsyntaxtextarea-%latest%",
            "slf4j-api-%latest%",
            "ws-commons-util-%latest%",
            "xmldb-api-%latest%",
            "xmlrpc-client-%latest%",
            "xmlrpc-common-%latest%"
    };

    // Resolves jar file patterns from jars[].
    private final LatestFileResolver jarFileResolver = new LatestFileResolver();

    /**
     * Get jar file specified by file pattern.
     *
     * @param folder          Directory containing the jars.
     * @param jarFileBaseName Name of jar file, including %latest% token if
     *                        necessary sans .jar file extension.
     * @return File object of jar file, null if not found.
     */
    private Path getJarFromLocation(final Path folder, final String jarFileBaseName) {
        final String fileToFind = folder.normalize().toAbsolutePath().toString() + java.io.File.separatorChar + jarFileBaseName + ".jar";
        final String resolvedFile = jarFileResolver.getResolvedFileName(fileToFind);
        final Path jar = Paths.get(resolvedFile).normalize();
        if (Files.exists(jar)) {
            LOGGER.debug("Found match: {} for file pattern: {}", resolvedFile, fileToFind);
            return jar;

        } else {
            LOGGER.error("Could not resolve file pattern: {}", fileToFind);
            return null;
        }
    }

    // Copy jars from map to list
    private void addToJars(final Path jar) {
        if (jar != null && FileUtils.fileName(jar).endsWith(".jar")) {
            allFiles.put(FileUtils.fileName(jar), jar);

            // Add jar.pack.gz if existent
            final Path pkgz = getJarPackGz(jar);
            if (pkgz != null) {
                allFiles.put(FileUtils.fileName(pkgz), pkgz);
            }
        }
    }

    /**
     * Creates a new instance of JnlpJarFiles
     *
     * @param libDir the lib folder.
     */
    public JnlpJarFiles(final Path libDir) {
        LOGGER.info("Initializing jar files Webstart");

        LOGGER.debug("Number of webstart jars={}", allJarNames.length);

        // Setup jars
        for (final String jarname : allJarNames) {
            final Path location = getJarFromLocation(libDir, jarname);
            addToJars(location);
        }

        // Setup exist-core-x.y.z.jar
        mainJar = getJarFromLocation(libDir, "exist-core-%latest%");
        addToJars(mainJar);
    }

    /**
     * Get All jar file as list.
     *
     * @return list of jar files.
     */
    public List<Path> getAllWebstartJars() {
        final List<Path> allWebstartJars = allFiles.values().stream().filter((file) -> (FileUtils.fileName(file).endsWith(".jar"))).collect(Collectors.toList());
        allWebstartJars.sort(Comparator.comparing(p -> p.toAbsolutePath().toString()));
        return allWebstartJars;
    }

    /**
     * Get file reference for JAR file.
     *
     * @param key the key for the JAR file.
     *
     * @return Reference to the jar file, NULL if not existent.
     */
    public Path getJarFile(final String key) {
        return allFiles.get(key);
    }

    private Path getJarPackGz(final Path jarName) {
        final String path = jarName.toAbsolutePath() + ".pack.gz";
        final Path pkgz = Paths.get(path);

        if (Files.exists(pkgz)) {
            return pkgz;
        }

        return null;
    }

    /**
     * Get last modified of main JAR file.
     *
     * @return the last modified time, or -1 if unknown.
     *
     * @throws IOException if the last modified time cannot be retrieved.
     */
    public long getLastModified() throws IOException {
        return (mainJar == null) ? -1 : Files.getLastModifiedTime(mainJar).toMillis();
    }
}
