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

import java.io.EOFException;
import java.io.File;
import java.io.IOException;

import java.net.SocketException;
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
    
    private JnlpJarFiles jf=null;
    private JnlpHelper jh=null;
    
    /**
     * Initialize servlet.cd
     */
    public void init() {
        logger.info("Initializing JNLP servlet");
        
        String realPath = getServletContext().getRealPath("/");
        if(realPath==null){
            logger.error("getServletContext().getRealPath() did not return a "+
                    "value. Webstart is not available.");
        }
        File contextRoot=new File( realPath );
 
        jh = new JnlpHelper(contextRoot);
        jf = new JnlpJarFiles(jh);
        
    }
    
    private String stripFilename(String URI){
        int lastPos=URI.lastIndexOf("/");
        return URI.substring(lastPos+1);
    }
    
    /**
     *  Handle webstart request for JNLP file, jar file or image.
     *
     * @param request   Object representing http request.
     * @param response  Object representing http response.
     * @throws ServletException  Standard servlet exception
     * @throws IOException       Standard IO exception
     */
    public void doGet(HttpServletRequest request, HttpServletResponse response)
                                           throws ServletException, IOException{

        try {
            JnlpWriter jw=new JnlpWriter();

            String URI = request.getRequestURI();
            logger.debug("Requested URI="+URI);

            if(URI.endsWith(".jnlp")){
                jw.writeJnlpXML(jf, request, response);

            } else if (URI.endsWith(".jar")){
                String filename = stripFilename( request.getPathInfo() );
                jw.sendJar(jf, filename, request, response);

            } else if ( URI.endsWith(".gif") || URI.endsWith(".jpg") ){
                String filename =  stripFilename( request.getPathInfo() );
                jw.sendImage(jh, jf, filename, response);

            } else {
                logger.error("Invalid filename extension.");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid filename extension.");
                return;
            }

        } catch(EOFException ex) {
            logger.debug(ex.getMessage(), ex);

        } catch(SocketException ex) {
            logger.debug(ex.getMessage(), ex);

        } catch (Throwable e){
            logger.error(e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
        }
        
    }
}