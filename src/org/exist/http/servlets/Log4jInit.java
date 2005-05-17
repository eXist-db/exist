/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
 *  TODO: Add CVS tag.
 */

package org.exist.http.servlets;

import java.io.File;
import java.io.IOException;
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
     * Initialize servlet for log4j purposes.
     */
    public void init() throws ServletException {
        
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
     *  Empty method.
     *
     * @param req HTTP Request object
     * @param res HTTP Response object
     */
    public void doGet(HttpServletRequest req, HttpServletResponse res) {
        //
    }
    
    /**
     *  Empty method.
     *
     * @param req HTTP Request object
     * @param res HTTP Response object
     */
    public void doPost(HttpServletRequest req, HttpServletResponse res) {
        //
    }
}
