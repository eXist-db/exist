/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *
 * $Id$
 */

package org.exist.webstart;

import java.io.File;

import org.apache.log4j.Logger;
import org.exist.start.LatestFileResolver;

/**
 *  Class for managing webstart jar files.
 *
 * @author Dannes Wessels
 */
public class JnlpJarFiles {
    
    private static Logger logger = Logger.getLogger(JnlpJarFiles.class);
    
    // Holders for jar files
    private File[] _coreJars;
    private File _mainJar;
    
    // Names of core jar files sans ".jar" extension.
    // Use %latest% token in place of a version string.
    private String jars[] = new String[]{
        "xmldb",
        "xmlrpc-common-%latest%",
        "xmlrpc-client-%latest%",
        "ws-commons-util-%latest%",
        "commons-pool-%latest%",
        "excalibur-cli-%latest%",
        "jEdit-syntax",
        "jline-%latest%",
        "log4j-%latest%",
        "stax-api-%latest%",
        "sunxacml-%latest%"
    };
    
    // Resolves jar file patterns from jars[].
    private LatestFileResolver jarFileResolver = new LatestFileResolver();
    
    /**
     * Get jar file specified by file pattern.
     * @param folder  Directory containing the jars.
     * @param jarFileBaseName  Name of jar file, including %latest% token if
     * necessary sans .jar file extension.
     * @return File object of jar file, null if not found.
     */
    public File getJar(File folder, String jarFileBaseName){
        String fileToFind = folder.getAbsolutePath() + File.separatorChar
                + jarFileBaseName + ".jar";
        String resolvedFile = jarFileResolver.getResolvedFileName(
                fileToFind
                );
        File jar = new File(resolvedFile);
        if (jar.exists()) {
            logger.debug(
                    "Found match: " + resolvedFile
                    + " for file pattern: " + fileToFind
                    );
            return jar;
        } else {
            logger.warn("Could not resolve file pattern: " + fileToFind);
            return null;
        }
    }
    
    /**
     * Creates a new instance of JnlpJarFiles
     *
     * @param jnlpHelper
     */
    public JnlpJarFiles(JnlpHelper jnlpHelper) {
        logger.info("Initializing jar files Webstart");
        
        // Setup array CORE jars
        int nrCoreJars=jars.length;
        _coreJars = new File[nrCoreJars];
        logger.debug("Number of webstart jars="+nrCoreJars);
        
        // Setup CORE jars
        for(int i=0;i<nrCoreJars;i++){
            _coreJars[i]=getJar(jnlpHelper.getCoreJarsFolder(), jars[i]);
        }
        
        // Setup exist.jar
        _mainJar=new File(jnlpHelper.getExistJarFolder(), "exist.jar");
    }
    
    
    
    /**
     * Get references to all "core" jar files.
     * @return Array of Files.
     */
    public File[] getCoreJars() {   return _coreJars;         }
    
    /**
     * Get references to all "exist" jar files.
     * @return Reference to exist.jar.
     */
    public File getMainJar() { return _mainJar;       }
    
    /**
     * Setter for property mainJar.
     * @param mainJar New value of property mainJar.
     */
    public void setMainJar(File mainJar) { _mainJar = mainJar;     }
    
    /**
     *  Get File reference of associated jar-file.
     * @param name
     * @return File reference to resource.
     */
    public File getFile(String name){
        File retVal=null;
        if(name.equals("exist.jar")){
            retVal = _mainJar;
            
        } else {
            boolean found=false;
            int index=0;
            while(!found && index < _coreJars.length ){
                if(_coreJars[index].getName().equals(name)){
                    found=true;
                    retVal=_coreJars[index];
                }  else {
                    index++;
                }
            }
        }
        return retVal;
    }
    
}
