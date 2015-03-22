/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-15 The eXist Project
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

import com.bradmcevoy.io.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Class for writing JNLP file, jar files and image files.
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
    private static final Logger LOGGER = LogManager.getLogger(JnlpWriter.class);

    /**
     * Write JNLP xml file to browser.
     *
     * @param response Object for writing to end user.
     * @throws java.io.IOException
     */
    void writeJnlpXML(JnlpJarFiles jnlpFiles, HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        LOGGER.debug("Writing JNLP file");

        // Format URL: "http://host:8080/CONTEXT/webstart/exist.jnlp"
        final String currentUrl = request.getRequestURL().toString();

        // Find BaseUrl http://host:8080/CONTEXT
        final int webstartPos = currentUrl.indexOf("/webstart");
        final String existBaseUrl = currentUrl.substring(0, webstartPos);

        // Find codeBase for jarfiles http://host:8080/CONTEXT/webstart/
        final String codeBase = existBaseUrl + "/webstart/";

        // Perfom sanity checks
        int counter = 0;
        for (final File jar : jnlpFiles.getAllWebstartJars()) {
            counter++; // debugging
            if (jar == null || !jar.exists()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, String.format("Missing Jar file! (%s)", counter));
                return;
            }
        }


        // Find URL to connect to with client
        final String startUrl = existBaseUrl.replaceFirst("http:", "xmldb:exist:")
                .replaceFirst("https:", "xmldb:exist:").replaceAll("-", "%2D") + "/xmlrpc";

//        response.setDateHeader("Last-Modified", mainJar.lastModified());
        response.setContentType("application/x-java-jnlp-file");
        try {
            final XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(response.getOutputStream());

            writer.writeStartDocument();
            writer.writeStartElement("jnlp");
            writer.writeAttribute("spec", "7.0");
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
            writer.writeAttribute("href", "jnlp_icon_128x128.gif");
            writer.writeAttribute("width", "128");
            writer.writeAttribute("height", "128");
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

            writer.writeStartElement("java");
            writer.writeAttribute("version", "1.8+");
            writer.writeEndElement();

            for (final File jar : jnlpFiles.getAllWebstartJars()) {
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
            
            if(request.isSecure()){
                writer.writeStartElement("argument");
                writer.writeCharacters("--use-ssl");
                writer.writeEndElement();
            }

            writer.writeEndElement(); // application-desc

            writer.writeEndElement(); // jnlp

            writer.writeEndDocument();

            writer.flush();
            writer.close();

        } catch (final Throwable ex) {
            LOGGER.error(ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage());
        }

    }

    /**
     * Send JAR or JAR.PACK.GZ file to end user.
     *
     * @param filename Name of JAR file
     * @param response Object for writing to end user.
     * @throws java.io.IOException
     */
    void sendJar(JnlpJarFiles jnlpFiles, String filename,
            HttpServletRequest request, HttpServletResponse response) throws IOException {

        LOGGER.debug("Send jar file " + filename);

        final File localFile = jnlpFiles.getJarFile(filename);
        if (localFile == null || !localFile.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Jar file '" + filename + "' not found.");
            return;
        }


        LOGGER.debug("Actual file " + localFile.getAbsolutePath());

        if (localFile.getName().endsWith(".jar")) {
            //response.setHeader(CONTENT_ENCODING, JAR_MIME_TYPE);
            response.setContentType(JAR_MIME_TYPE);

        } else if (localFile.getName().endsWith(".jar.pack.gz")) {
            response.setHeader(CONTENT_ENCODING, PACK200_GZIP_ENCODING);
            response.setContentType(PACK_MIME_TYPE);
        }

        // It is very improbable that a 64 bit jar is needed, but
        // it is better to be ready
        // response.setContentLength(Integer.parseInt(Long.toString(localFile.length())));
        response.setHeader("Content-Length", Long.toString(localFile.length()));
        response.setDateHeader("Last-Modified", localFile.lastModified());

        final InputStream is = new FileInputStream(localFile);
        final OutputStream os = response.getOutputStream();

        try {
            IOUtils.copy(is, os);
            os.flush();

        } catch (final IllegalStateException ex) {
            LOGGER.debug(ex.getMessage());
            throw new IOException(ex.getMessage());

        } catch (final IOException ex) {
            LOGGER.debug("Ignore IOException for '" + filename + "'");
            throw ex;
            
        } finally {
            IOUtils.closeQuietly(os);
            IOUtils.closeQuietly(is);
        }


    }

    void sendImage(JnlpHelper jh, JnlpJarFiles jf, String filename, HttpServletResponse response) throws IOException {
        LOGGER.debug("Send image " + filename);

        String type = getImageMimeType(filename);
      
        final InputStream imageInputStream = this.getClass().getResourceAsStream("resources/"+filename); 
        if (imageInputStream == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, String.format("Image file '%s' not found.", filename));
            return;
        }
        
        // Copy data
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(imageInputStream, baos);
        IOUtils.closeQuietly(imageInputStream);
        
        // Setup HTTP headers
        response.setContentType(type);
        response.setContentLength(baos.size());

        final ServletOutputStream os = response.getOutputStream();

        try {
            IOUtils.write(baos.toByteArray(), os);
            os.flush();
            
        } catch (final IllegalStateException ex) {
            LOGGER.debug(ex.getMessage());
            
        } catch (final IOException ex) {
            LOGGER.debug("Ignored IOException for '" + filename + "' " + ex.getMessage());
            
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private String getImageMimeType(String filename) {
        String type = "unknown";
        switch (FileUtils.getExtension(filename)) {
            case ".gif":
                type = "image/gif";
                break;
            case ".png":
                type = "image/png";
                break;
            case ".jpg":
            case ".jpeg":
                type = "image/jpeg";
                break;
        }
        return type;
    }
}
