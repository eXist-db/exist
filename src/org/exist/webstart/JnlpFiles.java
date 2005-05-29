/*
 * eXist Open Source Native XML Database Copyright (C) 2001-03 Wolfgang M.
 * Meier meier@ifs.tu-darmstadt.de http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */

package org.exist.webstart;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

/**
 *  Class for managing webstart jar files.
 */
public class JnlpFiles {
    
    private static Logger logger = Logger.getLogger(JnlpFiles.class);
    
    // Holders for jar files
    private File[] _coreJars;
    private File _mainJar;
    
    // regexp patterns to be more robust for version changes
    private String jars[] = new String[]{
                "antlr\\.jar", 
                 "commons-pool-.*\\.jar",
                "excalibur-cli-.*\\.jar", 
                "jEdit-syntax\\.jar",
                "libreadline-java\\.jar", 
                "log4j-.*\\.jar",
                "xml-commons-resolver-.*\\.jar",
                "xmldb\\.jar", 
                "xmlrpc-.*-patched\\.jar"
    }; // TODO tricky, needs te be reviewed on a regular basis.
    
    private String existHome=System.getProperty("exist.home");
    
    /**
     *  Get jar file specified by regular expression.
     * @param folder  Directory containing the jars.
     * @param regExp  Regexp pattern
     * @return        File object to jar file, null if not found.
     */
    public File getJar(File folder, String regExp){
      
        File jarFile=null;
        boolean found=false;
        int index=0;
        
        File allFiles[]= folder.listFiles();
        if(allFiles==null){
            logger.error("No files found in "+folder.getAbsolutePath());
            allFiles = new File[0];
        }
        
        Pattern p = Pattern.compile(regExp);
        while(!found && index<allFiles.length){
            Matcher m = p.matcher(allFiles[index].getName());
            if( m.matches() ){
                jarFile=allFiles[index];
                found=true;
            }
            index++;
        }
        
        
        
        return jarFile;
    }
    
    /** Creates a new instance of JnlpFiles */
    public JnlpFiles() {
        logger.info("Initializing jar files Webstart");
        logger.debug("eXist home="+existHome);
        
        File coreJarsFolder=null;
        File existJarFolder=null;
        
        // Setup array CORE jars
        int nrCoreJars=jars.length;
        _coreJars = new File[nrCoreJars];
        logger.debug("Number of webstart jars="+nrCoreJars);
        
        // Setup path based on installation (in jetty, container)
        if(isInWarFile()){
            // all files mixed in existHome/lib/
            logger.debug("eXist is running in container (.war).");
            coreJarsFolder= new File(existHome, "lib/");
            existJarFolder= new File(existHome, "lib/");
            
        } else {
            // all files located in existHome/lib/core/
            logger.debug("eXist is running private jetty server.");
            coreJarsFolder= new File(existHome, "lib/core");
            existJarFolder= new File(existHome);
        }
        logger.debug("CORE jars location="+coreJarsFolder.getAbsolutePath());
        logger.debug("EXIST jars location="+existJarFolder.getAbsolutePath());
        
        // Setup CORE jars
        for(int i=0;i<nrCoreJars;i++){
            _coreJars[i]=getJar(coreJarsFolder, jars[i]);
        }
        
        // Setup exist.jar
        _mainJar=new File(existJarFolder, "exist.jar");
    }
    
    /**
     *  Check wether exist runs in Servlet container (as war file).
     * @return TRUE if exist runs in servlet container.
     */
    public boolean isInWarFile(){
        
        boolean retVal =true;
        if( new File(existHome, "lib/core").isDirectory() ) {
            retVal=false;
        }
        return retVal;
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
     * @param   Name of file
     * @return  File reference to resource.
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
