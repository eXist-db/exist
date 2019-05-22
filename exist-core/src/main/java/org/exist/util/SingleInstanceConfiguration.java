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

import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SingleInstanceConfiguration extends Configuration {
    
        /* FIXME:  It's not clear whether this class is meant to be a singleton (due to the static
         * file and existHome fields and static methods), or if we should allow many instances to
         * run around in the system.  Right now, any attempts to create multiple instances will
         * likely get the system confused.  Let's decide which one it should be and fix it properly.
         *
         * This class cannot be a singleton as it is possible to run multiple instances of the database
         * on the same system.
         */
    
    @SuppressWarnings("unused")
	private final static Logger LOG = LogManager.getLogger(SingleInstanceConfiguration.class); //Logger
    protected static Optional<Path> _configFile = Optional.empty(); //config file (conf.xml by default)
    protected static Optional<Path> _existHome = Optional.empty();
    

    public SingleInstanceConfiguration() throws DatabaseConfigurationException {
        this("conf.xml", Optional.empty());
    }
    
    public SingleInstanceConfiguration(final String configFilename) throws DatabaseConfigurationException {
        this(configFilename, Optional.empty());
    }
    
    public SingleInstanceConfiguration(String configFilename, Optional<Path> existHomeDirname) throws DatabaseConfigurationException {
    	super(configFilename, existHomeDirname);
    	_configFile = configFilePath;
    	_existHome = existHome;
    }
        
    /**
     * Returns the absolute path to the configuration file.
     *
     * @return the path to the configuration file
     */
    public static Optional<Path> getPath() {
        if (!_configFile.isPresent()) {
            final Path f = ConfigurationHelper.lookup("conf.xml");
            return Optional.of(f.toAbsolutePath());
        }
        return _configFile;
    }
    
    /**
     * Get folder in which the exist webapplications are found.
     * For default install ("jar install") and in webcontainer ("war install")
     * the location is different. (EXIST_HOME/webapps vs. TOMCAT/webapps/exist)
     *
     * @return folder.
     */
    public static Optional<Path> getWebappHome(){
        // if existHome is not set, try to do so.
        if (!_existHome.isPresent()){
        	_existHome = ConfigurationHelper.getExistHome();
        }

        return _existHome.map(h -> h.resolve("webapp"));
    }
}
