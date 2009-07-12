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

import java.io.ByteArrayInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.xml.DOMConfigurator;

import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;


/**
 * Helper servlet for initializing the log4j framework in a webcontainer.
 */
public class Log4jInit extends HttpServlet {
    
    private String getTimestamp(){
        return new Date().toString();
    }
    
    private void convertLogFile(File srcConfig, File destConfig, File logDir){
        
        // Step 1 read config file into memory
        String srcDoc = "not initialized";
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            FileInputStream is = new FileInputStream(srcConfig);
            
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf)) > 0) {
                baos.write(buf, 0, len);
            }
            
            is.close();
            baos.close();
            srcDoc = new String(baos.toByteArray());
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        
        // Step 2 ; substitute Patterns
        String destDoc = srcDoc.replaceAll("loggerdir", logDir.getAbsolutePath().replaceAll("\\\\","/"));
        
        // Step 3 ; write back to file
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(destDoc.getBytes());
            FileOutputStream fos =new FileOutputStream(destConfig);
            byte[] buf = new byte[1024];
            int len;
            while ((len = bais.read(buf)) > 0) {
                fos.write(buf, 0, len);
            }
            fos.close();
            bais.close();
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Initialize servlet for log4j purposes in servlet container (war file).
     */
    @Override
    public void init() throws ServletException {
        
        // We need to check how eXist is running. If eXist is started in a
        // servlet container like Tomcat, then initialization *is* needed.
        //
        // If eXist is started in its own jetty server, the logging is 
        // already initialized. All can, must and shall be skipped then.
        if(!isInWarFile()) {
            System.out.println("Logging already initialized. Skipping...");
            return;
        }
        
        System.out.println("============= eXist Initialization =============" );
        
        // Get data from web.xml
        String file   = getInitParameter("log4j-init-file");
		String logdir = getInitParameter("log4j-log-dir");
		
		if( logdir == null ) {
			// Use default location for exist logs if not specified in web.xml
			logdir = "WEB-INF/logs";
		}
        
        // Get path where eXist is running
        String existDir =  getServletContext().getRealPath("/");
        
        // Define location of logfiles
        File logsdir = new File(existDir, logdir );
        logsdir.mkdirs();
        
        System.out.println(getTimestamp() + " - eXist logs dir="
                + logsdir.getAbsolutePath());
        
        // Get log4j configuration file
        File srcConfigFile = new File(existDir,file);
        
        // Convert
        File log4jConfigFile = new File(existDir, "WEB-INF/TMPfile.xml");
        convertLogFile(srcConfigFile, log4jConfigFile, logsdir);
        
        System.out.println(getTimestamp() + " - eXist log4j configuration=" 
                + log4jConfigFile.getAbsolutePath());

        
        // Configure log4j
        DOMConfigurator.configure(log4jConfigFile.getAbsolutePath());

        // Setup exist
        File eXistConfigFile = new File(existDir, "WEB-INF/conf.xml" );
        System.out.println(getTimestamp() + " - eXist-DB configuration=" 
                + eXistConfigFile.getAbsolutePath());
        try {
            /*Configuration config = */ new Configuration(eXistConfigFile.getAbsolutePath());
        } catch (DatabaseConfigurationException ex) {
            ex.printStackTrace();
        }
        
        System.out.println("================================================" );
    }
    
    /**
     *  Check wether exist runs in Servlet container (as war file).
     * @return TRUE if exist runs in servlet container.
     */
    public boolean isInWarFile(){
        boolean retVal =true;
        if (new File(ConfigurationHelper.getExistHome(), "lib/core").isDirectory()) {
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
    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res) {
        //
    }
    
}
