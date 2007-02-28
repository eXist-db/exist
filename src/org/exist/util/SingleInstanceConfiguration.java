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
 *  $Id: Configuration.java 5400 2007-02-25 13:20:15Z wolfgang_m $
 */
package org.exist.util;

import java.io.File;
import java.io.IOException;

import org.apache.log4j.Logger;

public class SingleInstanceConfiguration extends Configuration {
    
        /* FIXME:  It's not clear whether this class is meant to be a singleton (due to the static
         * file and existHome fields and static methods), or if we should allow many instances to
         * run around in the system.  Right now, any attempts to create multiple instances will
         * likely get the system confused.  Let's decide which one it should be and fix it properly.
         *
         * This class cannot be a singleton as it is possible to run multiple instances of the database
         * on the same system.
         */
    
    private final static Logger LOG = Logger.getLogger(SingleInstanceConfiguration.class); //Logger
    protected static String _configFile = null; //config file (conf.xml by default)
    protected static File _existHome = null;
    

    public SingleInstanceConfiguration() throws DatabaseConfigurationException {
        this("conf.xml", null);
    }
    
    public SingleInstanceConfiguration(String configFilename) throws DatabaseConfigurationException {
        this(configFilename, null);
    }
    
    public SingleInstanceConfiguration(String configFilename, String existHomeDirname) throws DatabaseConfigurationException {
    	super(configFilename, existHomeDirname);
    	_configFile = configFilePath;
    	_existHome = existHome;
    }
        
    /**
     * Returns the absolute path to the configuration file.
     *
     * @return the path to the configuration file
     */
    public static String getPath() {
        if (_configFile == null) {
            File f = ConfigurationHelper.lookup("conf.xml");
            return f.getAbsolutePath();
        }
        return _configFile;
    }
    
    /**
     *  Check wether exist runs in Servlet container (as war file).
     * @return TRUE if exist runs in servlet container.
     */
    public static boolean isInWarFile(){
        
        boolean retVal =true;
        
        // if existHome is not set,try to do so.
        if (_existHome == null){
            ConfigurationHelper.getExistHome();
        }
        
        if( new File(_existHome, "lib/core").isDirectory() ) {
            retVal=false;
        }
        return retVal;
    }
    
    /**
     *  Get folder in which the exist webapplications are found.
     * For default install ("jar install") and in webcontainer ("war install")
     * the location is different. (EXIST_HOME/webapps vs. TOMCAT/webapps/exist)
     *
     * @return folder.
     */
    public static File getWebappHome(){
        File webappFolder =null;
        
        // if existHome is not set,try to do so.
        if (_existHome == null){
        	ConfigurationHelper.getExistHome();
        }
        
        if(isInWarFile()){
            webappFolder= new File(_existHome, "..");
        } else {
            webappFolder= new File(_existHome, "webapp");
        }
        
        // convert to real path
        try {
            File tmpFolder = webappFolder.getCanonicalFile();
            webappFolder=tmpFolder;
        } catch (IOException ex) {
            // oops ; use previous path
        }
        
        return webappFolder;
    }
    
    /**
     * Returns <code>true</code> if the directory <code>dir</code> contains a file
     * named <tt>conf.xml</tt>.
     *
     * @param dir the directory
     * @return <code>true</code> if the directory contains a configuration file
     */
    private static boolean containsConfig(File dir, String config) {
        if (dir != null && dir.exists() && dir.isDirectory() && dir.canRead()) {
            File c = new File(dir, config);
            return c.exists() && c.isFile() && c.canRead();
        }
        return false;
    }
}
