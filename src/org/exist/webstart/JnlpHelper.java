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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 *
 * @author wessels
 */
public class JnlpHelper {
    
    private static Logger logger = Logger.getLogger(JnlpHelper.class);
    
    private String _currentUrl;
    private String _codeBase;
    private String _href;
    private String _startUrl;
    private String _existBaseUrl;
    
    private JnlpFiles _jnlpFiles;
    private HttpServletRequest _request;
    
    /** Creates a new instance of JnlpHelper */
    public JnlpHelper(JnlpFiles jnlpFiles, HttpServletRequest request) {
        
        // Store objects
        _jnlpFiles=jnlpFiles;
        _request=request;
        
        // Format URL: "http://host:8080/CONTEXT/webstart/exist.jnlp"
        _currentUrl = _request.getRequestURL().toString();
        
        // Find position of "/CONTEXT", construct URL to exist Base URL
        String contextPath = _request.getContextPath();
        int posContextInUrl=_currentUrl.indexOf(contextPath);
        _existBaseUrl = _currentUrl.substring(0, posContextInUrl) + contextPath;
        
        // Find URL to ...../webstart for first line jnlp-file
        int position = _currentUrl.lastIndexOf("/");
        _codeBase = _currentUrl.substring(0, position+1);
        _href = _currentUrl.substring(position+1);
        
        // Find URL to connect to with client
        _startUrl = _existBaseUrl.replace("http:", "xmldb:exist:") + "/xmlrpc";
    }
    
    /**
     * Get (external) URL where exist can be accessed.
     * @return eXist URL.
     */
    public String getExistBaseURL() { return _existBaseUrl;    }
    
    /**
     * Get (external) URL where webstart files can be accessed..
     * @return Webstart base URL.
     */
    public String getCodeBase() { return _codeBase;    }
    
    
    /**
     * Get relative name to JNLP file. Relative to _codeBase.
     * @return Name of JNLP file.
     */
    public String getHref() { return _href;    }
    
    /**
     * Get XML-rpc url for connecting the client.
     * @return XMLRPC url.
     */
    public String getStartUrl() {  return _startUrl;    }
    
    /**
     *  Write JNLP file to browser.
     * @param response  Object for writing to end user.
     * @throws java.io.IOException
     */
    void sendXML(HttpServletResponse response) throws IOException {
        
        logger.debug("Writing JNLP file");
        
        response.setContentType("application/x-java-jnlp-file");
        PrintWriter out = response.getWriter();
        
        out.println("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        out.println("<jnlp codebase=\"" + getCodeBase() + "\" href=\"" + getHref() +"\">" );
        out.println("<information>");
        out.println("<title>eXist XML-DB client</title>");
        out.println("<vendor>exist-db.org</vendor>");
        out.println("<homepage href=\"http://exist-db.org/\"/>");
        out.println("<description>Integrated command-line and gui client, "+
                "entirely based on the XML:DB API and provides commands "+
                "for most database related tasks, like creating and "+
                "removing collections, user management, batch-loading "+
                "XML data or querying.</description>");
        out.println("<description kind=\"short\">eXist XML-DB client</description>");
        out.println("<description kind=\"tooltip\">eXist XML-DB client</description>");
        
        out.println("<icon href=\""+_existBaseUrl+"/logo.jpg\" kind=\"splash\"/>");
        out.println("<icon href=\""+_existBaseUrl+"/logo.jpg\" />");
        
        
        out.println("</information>");
        out.println("<security>");
        out.println("<all-permissions />");
        out.println("</security>");
        
        out.println("<resources>");
        out.println("<j2se version=\"1.4+\"/>");
        
        File coreJars[] = _jnlpFiles.getCoreJars();
        for(int i=0 ; i<coreJars.length ; i++) {
            out.println("<jar href=\"" + coreJars[i].getName()
            + "\" size=\""+ coreJars[i].length() +"\" />");
        }
        
        out.println("<jar href=\"" + _jnlpFiles.getMainJar().getName()
        +"\" size=\""+ _jnlpFiles.getMainJar().length()
        + "\"  main=\"true\" />");
        out.println("</resources>");
        out.println("<application-desc main-class=\"org.exist.client.InteractiveClient\">");
        out.println("<argument>-ouri=" + getStartUrl()+ "</argument>");
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
    void sendJar(String filename, HttpServletResponse response ) throws IOException {
        
        logger.debug("Send jar file "+ filename);
        
        File jarFile = _jnlpFiles.getFile(filename);
        
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
}
