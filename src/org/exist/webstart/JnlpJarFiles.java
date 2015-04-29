/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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

package org.exist.webstart;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.start.LatestFileResolver;

/**
 *  Class for managing webstart jar files.
 *
 * @author Dannes Wessels
 */
public class JnlpJarFiles {
    
    private static final Logger LOGGER = LogManager.getLogger(JnlpJarFiles.class);
    
    private final Map<String, File> allFiles = new HashMap<>();
    private final File mainJar;
    
    // Names of core jar files sans ".jar" extension.
    // Use %latest% token in place of a version string.
    private final String allJarNames[] = new String[]{
        "xmldb",
        "xmlrpc-common-%latest%",
        "xmlrpc-client-%latest%",
        "ws-commons-util-%latest%",
        "commons-pool-%latest%",
        "commons-io-%latest%",
        "excalibur-cli-%latest%",
        "rsyntaxtextarea-%latest%",
        "jline-%latest%",
        "log4j-api-%latest%",
        "log4j-core-%latest%",
        "log4j-jul-%latest%",
        "log4j-slf4j-impl-%latest%",
        "slf4j-api-%latest%",
        "sunxacml-%latest%"
    };
    
    // Resolves jar file patterns from jars[].
    private final LatestFileResolver jarFileResolver = new LatestFileResolver();
    
    /**
     * Get jar file specified by file pattern.
     * @param folder  Directory containing the jars.
     * @param jarFileBaseName  Name of jar file, including %latest% token if
     * necessary sans .jar file extension.
     * @return File object of jar file, null if not found.
     */
    private File getJarFromLocation(File folder, String jarFileBaseName){
        final String fileToFind = folder.getAbsolutePath() + File.separatorChar + jarFileBaseName + ".jar";
        final String resolvedFile = jarFileResolver.getResolvedFileName( fileToFind );
        final File jar = new File(resolvedFile);
        if (jar.exists()) {
            LOGGER.debug(String.format("Found match: %s for file pattern: %s", resolvedFile, fileToFind));
            return jar;
            
        } else {
            LOGGER.warn(String.format("Could not resolve file pattern: %s", fileToFind));
            return null;
        }
    }


    // Copy jars from map to list
    private void addToJars(File jar) {
        if (jar != null && jar.getName().endsWith(".jar")) {
            allFiles.put(jar.getName(), jar);

            // Add jar.pack.gz if existent
            final File pkgz = getJarPackGz(jar);
            if (pkgz != null) {
                allFiles.put(pkgz.getName(), pkgz);
            }
            
        }
    }
    
    /**
     * Creates a new instance of JnlpJarFiles
     *
     * @param jnlpHelper
     */
    public JnlpJarFiles(JnlpHelper jnlpHelper) {
        LOGGER.info("Initializing jar files Webstart");

        LOGGER.debug(String.format("Number of webstart jars=%s", allJarNames.length));
        
        // Setup CORE jars
        for(final String jarname : allJarNames){
            final File jar = getJarFromLocation(jnlpHelper.getCoreJarsFolder(), jarname);
            addToJars(jar);
         }
        
        // Setup exist.jar
        mainJar=new File(jnlpHelper.getExistJarFolder(), "exist.jar");
        addToJars(mainJar);
    }
    

    /**
     *  Get All jar file as list.
     *
     * @return list of jar files.
     */
    public List<File> getAllWebstartJars(){
        final List<File> corejars = new ArrayList<>();

        for(final File file: allFiles.values()){
            if(file.getName().endsWith(".jar")){
                corejars.add(file);
            }
        }

        return corejars;
    }
    

    /**
     * Get file reference for JAR file.
     * @param key
     * @return Reference to the jar file, NULL if not existent.
     */
    public File getJarFile(String key) {
        final File retVal = allFiles.get(key);
        return retVal;
    }

    private File getJarPackGz(File jarName) {
        final String path = jarName.getAbsolutePath() + ".pack.gz";
        final File pkgz = new File(path);

        if (pkgz.exists()) {
            return pkgz;
        }

        return null;
    }
    
    /**
     *  Get last modified of main JAR file 
     */
    public long getLastModified(){
        return (mainJar==null) ? -1 : mainJar.lastModified();
    }
    
}
;