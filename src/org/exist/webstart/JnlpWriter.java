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

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.apache.log4j.Logger;

/**
 *  Class for writing JNLP file, jar files and image files.
 *
 * @author Dannes Wessels
 */
public class JnlpWriter {

    public static final String JAR_MIME_TYPE = "application/x-java-archive";
    public static final String PACK_MIME_TYPE = "application/x-java-pack200";
    public static final String ACCEPT_ENCODING = "accept-encoding";
    public static final String CONTENT_TYPE = "content-type";
    public static final String CONTENT_ENCODING = "content-encoding";
    public static final String PACK200_GZIP_ENCODING = "pack200-gzip";
    private static Logger logger = Logger.getLogger(JnlpWriter.class);

    /**
     *  Write JNLP files (jnlp, jar, gif/jpg) to browser.
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
        String codeBase = existBaseUrl + "/webstart/";

        // Perfom sanity checks
        File mainJar = jnlpFiles.getMainJar();
        if (mainJar == null || !mainJar.exists()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Missing exist.jar !");
            return;
        }


        int counter = 0;
        for (File jar : jnlpFiles.getCoreJarFiles()) {
            counter++; // debugging
            if (jar == null || !jar.exists()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Missing Jar file! (" + counter + ")");
                return;
            }
        }



        // Find URL to connect to with client
        String startUrl = existBaseUrl.replaceFirst("http:", "xmldb:exist:").replaceAll("-", "%2D") + "/xmlrpc";

        response.setDateHeader("Last-Modified", mainJar.lastModified());
        response.setContentType("application/x-java-jnlp-file");
        try {
            XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(response.getOutputStream());


            writer.writeStartDocument();
            writer.writeStartElement("jnlp");
            writer.writeAttribute("spec", "1.0+");
            writer.writeAttribute("codebase", codeBase);
            writer.writeAttribute("href", "exist.jnlp");

            writer.writeStartElement("information");

            writer.writeStartElement("title");
            writer.writeCharacters("eXist XML-DB client");
            writer.writeEndElement();

            writer.writeStartElement("vendor");
            writer.writeCharacters("exist-db.org");
            writer.writeEndElement();

            writer.writeStartElement("homepage");
            writer.writeAttribute("href", "http://exist-db.org");
            writer.writeEndElement();

            writer.writeStartElement("description");
            writer.writeCharacters("Integrated command-line and gui client, "
                    + "entirely based on the XML:DB API and provides commands "
                    + "for most database related tasks, like creating and "
                    + "removing collections, user management, batch-loading "
                    + "XML data or querying.");
            writer.writeEndElement();

            writer.writeStartElement("description");
            writer.writeAttribute("kind", "short");
            writer.writeCharacters("eXist XML-DB client");
            writer.writeEndElement();

            writer.writeStartElement("description");
            writer.writeAttribute("kind", "tooltip");
            writer.writeCharacters("eXist XML-DB client");
            writer.writeEndElement();

            writer.writeStartElement("icon");
            writer.writeAttribute("href", "jnlp_logo.jpg");
            writer.writeEndElement();

            writer.writeStartElement("icon");
            writer.writeAttribute("href", "jnlp_icon_64x64.gif");
            writer.writeAttribute("width", "64");
            writer.writeAttribute("height", "64");
            writer.writeEndElement();

            writer.writeStartElement("icon");
            writer.writeAttribute("href", "jnlp_icon_32x32.gif");
            writer.writeAttribute("width", "32");
            writer.writeAttribute("height", "32");
            writer.writeEndElement();

            writer.writeEndElement(); // information

            writer.writeStartElement("security");
            writer.writeEmptyElement("all-permissions");
            writer.writeEndElement();

            // ----------

            writer.writeStartElement("resources");

            writer.writeStartElement("property");
            writer.writeAttribute("name", "jnlp.packEnabled");
            writer.writeAttribute("value", "true");
            writer.writeEndElement();

            writer.writeStartElement("j2se");
            writer.writeAttribute("version", "1.6+");
            writer.writeEndElement();

            writer.writeStartElement("jar");
            writer.writeAttribute("href", jnlpFiles.getMainJar().getName());
            writer.writeAttribute("size", "" + jnlpFiles.getMainJar().length());
            writer.writeAttribute("main", "true");
            writer.writeEndElement();

            for (File jar : jnlpFiles.getCoreJarFiles()) {
                writer.writeStartElement("jar");
                writer.writeAttribute("href", jar.getName());
                writer.writeAttribute("size", "" + jar.length());
                writer.writeEndElement();
            }

            writer.writeEndElement(); // resources


            writer.writeStartElement("application-desc");
            writer.writeAttribute("main-class", "org.exist.client.InteractiveClient");

            writer.writeStartElement("argument");
            writer.writeCharacters("-ouri=" + startUrl);
            writer.writeEndElement();

            writer.writeStartElement("argument");
            writer.writeCharacters("--no-embedded-mode");
            writer.writeEndElement();

            writer.writeEndElement(); // application-desc

            writer.writeEndElement(); // jnlp

            writer.writeEndDocument();


        } catch (Throwable ex) {
            logger.error(ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }

    }

    /**
     *  Send JAR or JAR.PACK.GZ file to end user.
     *
     * @param filename  Name of JAR file
     * @param response  Object for writing to end user.
     * @throws java.io.IOException
     */
    void sendJar(JnlpJarFiles jnlpFiles, String filename,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        logger.debug("Send jar file " + filename);

        File localFile = jnlpFiles.getFile(filename);
        if (localFile == null || !localFile.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Jar file '" + filename + "' not found.");
            return;
        }


        logger.debug("Actual file " + localFile.getAbsolutePath());

        if (localFile.getName().endsWith(".jar")) {
            //response.setHeader(CONTENT_ENCODING, JAR_MIME_TYPE);
            response.setContentType(JAR_MIME_TYPE);

        } else if (localFile.getName().endsWith(".jar.pack.gz")) {
            response.setHeader(CONTENT_ENCODING, PACK200_GZIP_ENCODING);
            response.setContentType(PACK_MIME_TYPE);
        }


        response.setContentLength(Integer.parseInt(Long.toString(localFile.length())));
        response.setDateHeader("Last-Modified", localFile.lastModified());

        FileInputStream fis = new FileInputStream(localFile);
        ServletOutputStream os = response.getOutputStream();

        try {
            // Transfer bytes from in to out
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) > 0) {
                os.write(buf, 0, len);
            }

        } catch (IllegalStateException ex) {
            logger.debug(ex.getMessage());

        } catch (IOException ex) {
            logger.debug("Ignore IOException for '" + filename + "'");
        }

        os.flush();
        os.close();
        fis.close();
    }

    void sendImage(JnlpHelper jh, JnlpJarFiles jf, String filename, HttpServletResponse response) throws IOException {
        logger.debug("Send image " + filename);

        File imagesFolder = new File(jh.getWebappFolder(), "resources");

        String type = null;
        if (filename.endsWith(".gif")) {
            type = "image/gif";
        } else {
            type = "image/jpeg";
        }

        response.setContentType(type);

        File imageFile = new File(imagesFolder, filename);
        if (imageFile == null || !imageFile.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND,
                    "Image file '" + filename + "' not found.");
            return;
        }

        response.setContentLength(Integer.parseInt(Long.toString(imageFile.length())));
        response.setDateHeader("Last-Modified", imageFile.lastModified());

        FileInputStream fis = new FileInputStream(imageFile);
        ServletOutputStream os = response.getOutputStream();

        try {
            // Transfer bytes from in to out
            byte[] buf = new byte[8096];
            int len;
            while ((len = fis.read(buf)) > 0) {
                os.write(buf, 0, len);
            }

        } catch (IllegalStateException ex) {
            logger.debug(ex.getMessage());

        } catch (IOException ex) {
            logger.debug("Ignore IOException for '" + filename + "'");
        }

        os.flush();
        os.close();
        fis.close();
    }

}
