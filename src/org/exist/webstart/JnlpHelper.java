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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *  Helper class for webstart.
 *
 * @author Dannes Wessels
 */
public class JnlpHelper {
    
    private final static String LIB_CORE  ="../lib/core";
    private final static String LIB_EXIST ="..";
    private final static String LIB_WEBINF="WEB-INF/lib/";
    
    private static Logger logger = LogManager.getLogger(JnlpHelper.class);
    
    private File coreJarsFolder=null;
    private File existJarFolder=null;
    private File webappsFolder=null;
    
    
    private boolean isInWarFile(File existHome){
            
        if( new File(existHome, LIB_CORE).isDirectory() ) {
            return false;
        }
        return true;
    }
    
    /** Creates a new instance of JnlpHelper */
    public JnlpHelper(File contextRoot) {
        
        // Setup path based on installation (in jetty, container)
        if(isInWarFile(contextRoot)){
            // all files mixed in contextRoot/WEB-INF/lib
            logger.debug("eXist is running in servlet container (.war).");
            coreJarsFolder= new File(contextRoot, LIB_WEBINF);
            existJarFolder= coreJarsFolder;
            webappsFolder=contextRoot;
            
        } else {
            //files located in contextRoot/lib/core and contextRoot
            logger.debug("eXist is running private jetty server.");
            coreJarsFolder= new File(contextRoot, LIB_CORE);
            existJarFolder= new File(contextRoot, LIB_EXIST);;
            webappsFolder=contextRoot;
        }
        
        logger.debug("CORE jars location="+coreJarsFolder.getAbsolutePath());
        logger.debug("EXIST jars location="+existJarFolder.getAbsolutePath());
        logger.debug("WEBAPP location="+webappsFolder.getAbsolutePath());
    }
    
    public File getWebappFolder(){
        return webappsFolder;
    }
    
    public File getCoreJarsFolder(){
        return coreJarsFolder;
    }
    
    public File getExistJarFolder(){
        return existJarFolder;
    }
    
}
