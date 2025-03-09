/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.util.ConfigurationHelper;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.SingleInstanceConfiguration;
import org.exist.xquery.Expression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.*;

import jakarta.servlet.http.HttpServletRequest;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;

import static javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING;

/**
 * Webapplication Descriptor
 *
 * Class representation of an XQuery Web Application Descriptor file
 * with some helper functions for performing Descriptor related actions
 * Uses the Singleton design pattern.
 *
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @version 1.71
 * @serial 2006-03-19
 */

// TODO: doLogRequestInReplayLog() - add the facility to log HTTP PUT requests, may need changes to HttpServletRequestWrapper
// TODO: doLogRequestInReplayLog() - add the facility to log HTTP POST form file uploads, may need changes to HttpServletRequestWrapper

public class Descriptor implements ErrorHandler {
    private static final String SYSTEM_LINE_SEPARATOR = System.lineSeparator();
    //References
    private static Descriptor singletonRef;
    private final static Logger LOG = LogManager.getLogger(Descriptor.class);        //Logger
    /**
     * descriptor file (descriptor.xml)
     */
    private final static String file = "descriptor.xml";

    //Data
    private BufferedWriter bufWriteReplayLog = null;    //Should a replay log of requests be created
    private boolean requestsFiltered = false;
    private String allowSourceList[] = null;    //Array of xql files to allow source to be viewed
    private String mapList[][] = null;                    //Array of Mappings

    /**
     * Descriptor Constructor.
     *
     * Class has a Singleton design pattern
     * to get an instance, call getDescriptorSingleton()
     */
    private Descriptor() {
        InputStream is = null;
        try {
            // First, try to read Descriptor from file. Guess the location if necessary
            // from the home folder.
            Path f = ConfigurationHelper.lookup(file);
            if (!Files.isReadable(f)) {
                f = f.getParent().resolve("etc").resolve(file);
                if (!Files.isReadable(f)) {
                    LOG.warn("Giving up unable to read descriptor file from {}", f);
                } else {
                    is = new BufferedInputStream(Files.newInputStream(f));
                }
            } else {
                is = new BufferedInputStream(Files.newInputStream(f));
                LOG.info("Reading Descriptor from file {}", f);
            }

            if (is == null) {
                // otherise, secondly
                // try to read the Descriptor from a file within the classpath
                is = Descriptor.class.getResourceAsStream(file);
                if (is != null) {
                    LOG.info("Reading Descriptor from classloader in {}", this.getClass().getPackage());
                } else {
                    LOG.warn("Giving up unable to read descriptor.xml file from classloader in {}", this.getClass().getPackage());
                    return;
                }
            }

            // initialize xml parser
            // we use eXist's in-memory DOM implementation to work
            // around a bug in Xerces
            final SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
            factory.setNamespaceAware(true);

            final InputSource src = new InputSource(is);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();

            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            reader.setFeature(FEATURE_SECURE_PROCESSING, true);

            final SAXAdapter adapter = new SAXAdapter((Expression) null);
            reader.setContentHandler(adapter);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            reader.parse(src);

            final Document doc = adapter.getDocument();

            //load <xquery-app> attribue settings
            if ("true".equals(doc.getDocumentElement().getAttribute("request-replay-log"))) {
                final Path logFile = Paths.get("request-replay-log.txt");
                bufWriteReplayLog = Files.newBufferedWriter(logFile);
                final String attr = doc.getDocumentElement().getAttribute("filtered");
                if (attr != null)
                    requestsFiltered = "true".equals(attr);
            }

            //load <allow-source> settings
            final NodeList allowsourcexqueries = doc.getElementsByTagName("allow-source");
            if (allowsourcexqueries.getLength() > 0) {
                configureAllowSourceXQuery((Element) allowsourcexqueries.item(0));
            }

            //load <maps> settings
            final NodeList maps = doc.getElementsByTagName("maps");
            if (maps.getLength() > 0) {
                configureMaps((Element) maps.item(0));
            }
        } catch (final SAXException | IOException | ParserConfigurationException e) {
            LOG.warn("Error while reading descriptor file: " + file, e);
            return;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (final IOException ioe) {
                    LOG.warn(ioe);
                }
            }
        }
    }

    /**
     * Returns a refernce to this (Descriptor) Singleton class
     *
     * @return The Descriptor object reference
     */
    public static synchronized Descriptor getDescriptorSingleton() {
        if (singletonRef == null) {
            singletonRef = new Descriptor();
        }

        return (singletonRef);
    }

    /**
     * loads {@code allow-source} settings from the descriptor.xml file
     *
     * @param    allowsourcexqueries    The &lt;allow-source&gt; DOM Element from the descriptor.xml file
     */
    private void configureAllowSourceXQuery(Element allowsourcexqueries) {
        //Get the xquery element(s)
        final NodeList nlXQuery = allowsourcexqueries.getElementsByTagName("xquery");

        //Setup the hashmap to hold the xquery elements
        allowSourceList = new String[nlXQuery.getLength()];

        Element elem = null; //temporary holds xquery elements

        //Iterate through the xquery elements
        for (int i = 0; i < nlXQuery.getLength(); i++) {
            elem = (Element) nlXQuery.item(i);                //<xquery>
            String path = elem.getAttribute("path");        //@path

            //must be a path to allow source for
            if (path == null) {
                LOG.warn("Error element 'xquery' requires an attribute 'path'");
                return;
            }
            path = path.replaceAll("\\$\\{WEBAPP_HOME\\}",
                    SingleInstanceConfiguration.getWebappHome().orElse(Paths.get(".")).toAbsolutePath().toString().replace('\\', '/'));

            //store the path
            allowSourceList[i] = path;
        }
    }

    /**
     * loads &lt;maps&gt; settings from the descriptor.xml file
     *
     * @param    maps    The &lt;maps&gt; DOM Element from the descriptor.xml file
     */
    private void configureMaps(Element maps) {
        //TODO: add pattern support for mappings, as an alternative to path - deliriumsky

        //Get the map element(s)
        final NodeList nlMap = maps.getElementsByTagName("map");

        //Setup the hashmap to hold the map elements
        mapList = new String[nlMap.getLength()][2];

        Element elem = null; //temporary holds map elements

        //Iterate through the map elements
        for (int i = 0; i < nlMap.getLength(); i++) {
            elem = (Element) nlMap.item(i);                    //<map>
            String path = elem.getAttribute("path");        //@path
            //String pattern = elem.getAttribute("pattern");//@pattern
            String view = elem.getAttribute("view");        //@view

            //must be a path or a pattern to map from
            if (path == null /*&& pattern == null*/) {
                LOG.warn("Error element 'map' requires an attribute 'path' or an attribute 'pattern'");
                return;
            }
            path = path.replaceAll("\\$\\{WEBAPP_HOME\\}",
                    SingleInstanceConfiguration.getWebappHome().orElse(Paths.get(".")).toAbsolutePath().toString().replace('\\', '/'));

            //must be a view to map to
            if (view == null) {
                LOG.warn("Error element 'map' requires an attribute 'view'");
                return;
            }
            view = view.replaceAll("\\$\\{WEBAPP_HOME\\}",
                    SingleInstanceConfiguration.getWebappHome().orElse(Paths.get(".")).toAbsolutePath().toString().replace('\\', '/'));

            //store what to map from
           /* if(path != null)
            {*/
            //store the path
            mapList[i][0] = path;
            /*}
            else
            {
            	//store the pattern
            	mapList[i][0] = pattern;
            }*/

            //store what to map to
            mapList[i][1] = view;
        }
    }

    /**
     * Determines whether it is permissible to show the source of an XQuery.
     * Takes a path such as that from RESTServer.doGet() as an argument,
     * if it finds a matching allowsourcexquery path in the descriptor then it returns true else it returns false
     *
     * @param path The path of the XQuery (e.g. /db/MyCollection/query.xql)
     * @return The boolean value true or false indicating whether it is permissible to show the source
     */
    public boolean allowSource(String path) {
        if (allowSourceList != null) {
            //Iterate through the xqueries that source viewing is allowed for
            for (String s : allowSourceList) {
                // DWES: this helps a lot. quickfix not the final solution
                path = path.replace('\\', '/');

                //does the path match the <allow-source><xquery path=""/></allow-source> path
                if ((s.equals(path)) || (path.contains(s))) {
                    //yes, return true
                    return (true);
                }
            }
        }
        return (false);
    }

    /**
     * Map's one XQuery or Collection path to another
     * Takes a path such as that from RESTServer.doGet() as an argument,
     * if it finds a matching map path then it returns the map view else it returns the passed in path
     *
     * @param path The path of the XQuery or Collection (e.g. /db/MyCollection/query.xql or /db/MyCollection) to map from
     * @return The path of the XQuery or Collection (e.g. /db/MyCollection/query.xql or /db/MyCollection) to map to
     */
    public String mapPath(String path) {
        if (mapList == null) //has a list of mappings been specified?
        {
            return (path);
        }

        //Iterate through the mappings
        for (String[] strings : mapList) {
            //does the path or the path/ match the map path
            if (strings[0].equals(path) || (strings[0] + "/").equals(path)) {
                //return the view
                return (strings[1]);
            }
        }

        //no match return the original path
        return (path);
    }

    public boolean requestsFiltered() {
        return requestsFiltered;
    }

    /**
     * Determines whether it is permissible to Log Requests.
     *
     * Enabled by descriptor.xml &lt;xquery-app request-replay-log="true"&gt;
     *
     * @return The boolean value true or false indicating whether it is permissible to Log Requests
     */
    public boolean allowRequestLogging() {
        return bufWriteReplayLog != null;
    }

    /**
     * Logs HTTP Request's in a log file suitable for replaying to eXist later
     * Takes a HttpServletRequest or a HttpServletRequestWrapper as an argument for logging.
     *
     * Enabled by descriptor.xml &lt;xquery-app request-replay-log="true"&gt;
     *
     * @param request The HttpServletRequest to log.
     *                For Simple HTTP POST Requests - EXistServlet/XQueryServlet - POST parameters (e.g. form data) will only be logged if a HttpServletRequestWrapper is used instead of HttpServletRequest! POST Uploaded files are not yet supported!
     *                For XML-RPC Requests - RpcServlet - HttpServletRequestWrapper must be used, otherwise the content of the Request will be lost!
     *                For Cocoon Requests  -
     */
    public synchronized void doLogRequestInReplayLog(HttpServletRequest request) {
        //Only log if set by the user in descriptor.xml <xquery-app request-replay-log="true">
        if (bufWriteReplayLog == null) {
            return;
        }

        //Log the Request
        try {
            //Store the date and time
            bufWriteReplayLog.write("Date: ");
            final SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
            bufWriteReplayLog.write(formatter.format(new Date()));

            bufWriteReplayLog.write(SYSTEM_LINE_SEPARATOR);

            //Store the request string excluding the first line
            final String requestAsString = request.toString();
            bufWriteReplayLog.write(requestAsString.substring(requestAsString.indexOf(SYSTEM_LINE_SEPARATOR) + 1));

            //End of record indicator
            bufWriteReplayLog.write(SYSTEM_LINE_SEPARATOR);

            //flush the buffer to file
            bufWriteReplayLog.flush();
        } catch (final IOException ioe) {
            LOG.warn("Could not write request replay log: {}", ioe.getMessage(), ioe);
            return;
        }
    }

    /**
     * Thows a CloneNotSupportedException as this class uses a Singleton design pattern
     *
     * @return Will never return anything!
     */
    public Object clone() throws CloneNotSupportedException {
        //Class is a Singleton, dont allow cloning
        throw new CloneNotSupportedException();
    }

    /**
     * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
     */
    @Override
    public void error(SAXParseException exception) throws SAXException {
        LOG.error("Error occurred while reading descriptor file [line: {}]:{}", exception.getLineNumber(), exception.getMessage(), exception);
    }

    /**
     * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
     */
    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        LOG.error("Error occurred while reading descriptor file [line: {}]:{}", exception.getLineNumber(), exception.getMessage(), exception);
    }

    /**
     * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
     */
    @Override
    public void warning(SAXParseException exception) throws SAXException {
        LOG.error("error occurred while reading descriptor file [line: {}]:{}", exception.getLineNumber(), exception.getMessage(), exception);
    }

}
