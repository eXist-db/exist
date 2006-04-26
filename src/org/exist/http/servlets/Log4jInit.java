/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */

package org.exist.http.servlets;

import java.io.File;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.xml.DOMConfigurator;


/**
 * Helper servlet for initializing the log4j framework in a webcontainer.
 */
public class Log4jInit extends HttpServlet {
    
    /**
     * Initialize servlet for log4j purposes in servlet container (war file).
     */
    public void init() throws ServletException {
 	   System.setProperty("user.dir", getServletContext().getRealPath("/"));
        
        // We need to check how eXist is running. If eXist is started in a
        // servlet container like Tomcat, then initialization *is* needed.
        //
        // If eXist is started in its own jetty server, the logging is 
        // already initialized. All can, must and shall be skipped then.
        if(!isInWarFile()) {
            System.out.println("Logging already initialized. Skipping...");
            return;
        }
        
        // Get data from web.xml
        String file = getInitParameter("log4j-init-file");
        
        // Get path where eXist is running
        String existDir =  getServletContext().getRealPath("/");
        
        // Define location of logfiles
        File logsdir = new File(existDir, "WEB-INF/logs" );
        logsdir.mkdirs();
        System.out.println("eXist logs dir="+ logsdir.getAbsolutePath());
        System.setProperty("logger.dir", logsdir.getAbsolutePath() );      
        
        // Get log4j configuration file
        File configFile = new File(existDir,file);
        System.out.println("eXist log4j configuration="+configFile.getAbsolutePath());
        
        // Configure log4j
        DOMConfigurator.configure(configFile.getAbsolutePath());
    }
    
    /**
     *  Check wether exist runs in Servlet container (as war file).
     * @return TRUE if exist runs in servlet container.
     */
    public boolean isInWarFile(){
        boolean retVal =true;
        if (new File(System.getProperty("exist.home"), "lib/core").isDirectory()) {
            retVal=false;
        }
        return retVal;
    }
    
    /**
     *  Empty method.
     *
     * @param req HTTP Request object
     * @param res HTTP Response object
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res) {
        //
    }
    
}
