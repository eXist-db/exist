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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 *  Class for writing JNLP file, jar files and image files.
 *
 * @author Dannes Wessels
 */
public class JnlpWriter {
    
    private static Logger logger = Logger.getLogger(JnlpWriter.class);
    
    
    /**
     *  Write JNLP file to browser.
     * @param response  Object for writing to end user.
     * @throws java.io.IOException
     */
    void writeJnlpXML(JnlpJarFiles jnlpFiles, HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        
        logger.debug("Writing JNLP file");
                
        // Format URL: "http://host:8080/CONTEXT/webstart/exist.jnlp"
        String currentUrl = request.getRequestURL().toString();
        
        // Find BaseUrl http://host:8080/CONTEXT
        int webstartPos = currentUrl.indexOf("/webstart");
        String existBaseUrl = currentUrl.substring(0, webstartPos);
        
        // Find codeBase for jarfiles http://host:8080/CONTEXT/webstart/
        String codeBase = existBaseUrl+"/webstart/";
               
        // Find URL to connect to with client
        String startUrl = existBaseUrl
                .replaceFirst("http:", "xmldb:exist:")
                .replaceAll("-", "%2D") + "/xmlrpc";
        
        
        response.setContentType("application/x-java-jnlp-file");
        PrintWriter out = response.getWriter();
        
        out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        out.println("<jnlp spec=\"1.0+\" codebase=\"" + codeBase + "\" href=\"exist.jnlp\">" );
        out.println("<information>");
        out.println("  <title>eXist XML-DB client</title>");
        out.println("  <vendor>exist-db.org</vendor>");
        out.println("  <homepage href=\"http://exist-db.org/\"/>");
        out.println("  <description>Integrated command-line and gui client, "+
                "entirely based on the XML:DB API and provides commands "+
                "for most database related tasks, like creating and "+
                "removing collections, user management, batch-loading "+
                "XML data or querying.</description>");
        out.println("  <description kind=\"short\">eXist XML-DB client</description>");
        out.println("  <description kind=\"tooltip\">eXist XML-DB client</description>");
        
        out.println("  <icon href=\"jnlp_logo.jpg\" kind=\"splash\"/>");
        out.println("  <icon href=\"jnlp_logo.jpg\" />");
        out.println("  <icon href=\"jnlp_icon_64x64.gif\" width=\"64\" height=\"64\" />");
        out.println("  <icon href=\"jnlp_icon_32x32.gif\" width=\"32\" height=\"32\" />");
        
        out.println("</information>");
        out.println("<security>");
        out.println("  <all-permissions />");
        out.println("</security>");
        
        out.println("<resources>");
        out.println("<j2se version=\"1.4+\"/>");

        out.println("  <jar href=\"" + jnlpFiles.getMainJar().getName()
            +"\" size=\""+ jnlpFiles.getMainJar().length()
            + "\"  main=\"true\" />");
                
        File coreJars[] = jnlpFiles.getCoreJars();
        for(int i=0 ; i<coreJars.length ; i++) {
            out.println("  <jar href=\"" + coreJars[i].getName()
            + "\" size=\""+ coreJars[i].length() +"\" />");
        }

        out.println("</resources>");
        out.println("<application-desc main-class=\"org.exist.client.InteractiveClient\">");
        out.println("  <argument>-ouri=" +  startUrl+ "</argument>");
        out.println("  <argument>--no-embedded-mode</argument>");
        out.println("</application-desc>");
        out.println("</jnlp>");
        out.flush();
        out.close();
        
    }
    
    /**
     *  Send JAR file to end user.
     * @param filename  Name of JAR file
     * @param response  Object for writing to end user.
     * @throws java.io.IOException
     */
    void sendJar(JnlpJarFiles jnlpFiles, String filename, 
                 HttpServletResponse response ) throws IOException {
        
        logger.debug("Send jar file "+ filename);
        
        File jarFile = jnlpFiles.getFile(filename);
        
        response.setContentType("application/x-java-archive");
        response.setContentLength( Integer.parseInt(Long.toString(jarFile.length())) );
        response.setDateHeader("Last-Modified",jarFile.lastModified());
        
        FileInputStream fis = new FileInputStream( jarFile );
        OutputStream os = response.getOutputStream();
        
        
        // Transfer bytes from in to out
        byte[] buf = new byte[8096];
        int len;
        while ((len = fis.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
        
        os.flush();
        os.close();
        fis.close();
    }

    void sendImage(JnlpHelper jh, JnlpJarFiles jf, String filename, HttpServletResponse response) throws IOException {
        logger.debug("Send image "+ filename);
        
        File imagesFolder = new File(jh.getWebappFolder() , "resources");
        
        String type="";
        if(filename.endsWith(".gif")){
            type="image/gif";
        } else {
            type="image/jpeg";
        }
        
        response.setContentType(type);
        
        File imageFile = new File(imagesFolder, filename);
        
        response.setContentLength( Integer.parseInt(Long.toString(imageFile.length())) );
        response.setDateHeader("Last-Modified",imageFile.lastModified());
        
        FileInputStream fis = new FileInputStream( imageFile );
        OutputStream os = response.getOutputStream();
        
        
        // Transfer bytes from in to out
        byte[] buf = new byte[8096];
        int len;
        while ((len = fis.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
        
        os.flush();
        os.close();
        fis.close();
    }
}
