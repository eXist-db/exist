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
import org.exist.util.ConfigurationHelper;
import org.exist.util.SingleInstanceConfiguration;

/**
 *  Helper class for webstart.
 *
 * @author Dannes Wessels
 */
public class JnlpHelper {
    
    private static Logger logger = Logger.getLogger(JnlpHelper.class);
    private File existHome = ConfigurationHelper.getExistHome();
    
    private File coreJarsFolder=null;
    private File existJarFolder=null;
    private File webappFolder=null;
    
    /** Creates a new instance of JnlpHelper */
    public JnlpHelper() {
        
        // Setup path based on installation (in jetty, container)
        if(SingleInstanceConfiguration.isInWarFile()){
            // all files mixed in existHome/lib/
            logger.debug("eXist is running in container (.war).");
            coreJarsFolder= new File(existHome, "lib/");
            existJarFolder= new File(existHome, "lib/");
            
        } else {
            // all files located in existHome/lib/core/
            logger.debug("eXist is running private jetty server.");
            coreJarsFolder= new File(existHome, "lib/core");
            existJarFolder= existHome;
        }
        
        webappFolder=SingleInstanceConfiguration.getWebappHome();
        
        logger.debug("CORE jars location="+coreJarsFolder.getAbsolutePath());
        logger.debug("EXIST jars location="+existJarFolder.getAbsolutePath());
        logger.debug("WEBAPP location="+webappFolder.getAbsolutePath());
    }
    
    public File getWebappFolder(){
        return webappFolder;
    }
    
    public File getCoreJarsFolder(){
        return coreJarsFolder;
    }
    
    public File getExistJarFolder(){
        return existJarFolder;
    }
    
}
