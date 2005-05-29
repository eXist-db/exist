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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * Dedicated servlet for Webstart.
 */
public class JnlpServlet extends HttpServlet {
    
    private static Logger logger = Logger.getLogger(JnlpServlet.class);
    
    private JnlpFiles jf=null;
    
    /**
     * Initialize servlet.
     */
    public void init() {
        logger.info("Initializing JNLP servlet");
        
        // Pre-find al relevant files
        jf = new JnlpFiles();
    }
    
    /**
     *  Handle enduser webstart request.
     * @param req   Object representing http request.
     * @param resp  Object representing http response.
     * @throws javax.servlet.ServletException 
     * @throws java.io.IOException 
     */
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
    throws ServletException, IOException{
        
        JnlpHelper jh=new JnlpHelper(jf, req);
        
        String URI = req.getRequestURI();
        logger.debug("Requested URI="+URI);
        
        if(URI.endsWith(".jnlp")){
            jh.sendXML(resp);
            
        } else if (URI.endsWith(".jar")){
            String filename= req.getPathInfo().substring(1);
            jh.sendJar(filename, resp);
            
        } else {
            logger.error("Invalid file type");
            throw new ServletException("Invalid file type");            
        }
    }
}