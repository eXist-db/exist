/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-10 The eXist Project
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
package org.exist.management.client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Logger;
import org.exist.util.ConfigurationHelper;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;

/**
 * A servlet to monitor the database. It returns status information for the database based on the JMX interface. For
 * simplicity, the JMX beans provided by eXist are organized into categories. One calls the servlet with one or more
 * categories in parameter "c", e.g.:
 *
 * /exist/jmx?c=instances&c=memory
 *
 * If no parameter is specified, all categories will be returned. Valid categories are "memory", "instances", "disk",
 * "system", "caches", "locking", "processes", "sanity", "all".
 *
 * The servlet can also be used to test if the database is responsive by using parameter "operation=ping" and a timeout
 * (t=timeout-in-milliseconds). For example, the following call
 *
 * /exist/jmx?operation=ping&t=1000
 *
 * will wait for a response within 1000ms. If the ping returns within the specified timeout, the servlet returns the
 * attributes of the SanityReport JMX bean, which will include an element &lt;jmx:Status&gt;PING_OK&lt;/jmx:Status&gt;.
 * If the ping takes longer than the timeout, you'll instead find an element &lt;jmx:error&gt; in the returned XML. In
 * this case, additional information on running queries, memory consumption and database locks will be provided.
 *
 * @author wolf
 *
 */
public class JMXServlet extends HttpServlet {

    protected final static Logger LOG = Logger.getLogger(JMXServlet.class);

    private final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    }

    private JMXtoXML client;
    private final Set<String> localhostAddresses = new HashSet<>();
    private File tokenFile = null;


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // Verify if request is from localhost or if user has specific servlet/container managed role.
        if (isFromLocalHost(request)) {
            // Localhost is always authorized to access
            LOG.info("Local access");

        } else if (hasSecretToken(request, createGetToken())) {         
            // Correct token is provided
            LOG.info("COrrect token provided by " + request.getRemoteHost());
            
        } else  {
            String authHeader = request.getHeader("Authorization");
            if (authHeader == null) {
                response.setHeader("WWW-Authenticate", "basic realm=\"JMXservlet\"");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            if (!request.isUserInRole("admin")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Incorrect role");
                return;
            }


        }

        Element root = null;

        final String operation = request.getParameter("operation");
        if ("ping".equals(operation)) {
            long timeout = 5000;
            final String timeoutParam = request.getParameter("t");
            if (timeoutParam != null) {
                try {
                    timeout = Long.parseLong(timeoutParam);
                } catch (final NumberFormatException e) {
                    throw new ServletException("timeout parameter needs to be a number. Got: " + timeoutParam);
                }
            }

            final long responseTime = client.ping("exist", timeout);
            if (responseTime == JMXtoXML.PING_TIMEOUT) {
                root = client.generateXMLReport(String.format("no response on ping after %sms", timeout),
                        new String[]{"sanity", "locking", "processes", "instances", "memory"});
            } else {
                root = client.generateXMLReport(null, new String[]{"sanity"});
            }

        } else {
            String[] categories = request.getParameterValues("c");
            if (categories == null) {
                categories = new String[]{"all"};
            }
            root = client.generateXMLReport(null, categories);
        }

        response.setContentType("application/xml");

        final Object useAttribute = request.getAttribute("jmx.attribute");
        if (useAttribute != null) {
            request.setAttribute(useAttribute.toString(), root);

        } else {
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), "UTF-8");
            final DOMSerializer streamer = new DOMSerializer(writer, defaultProperties);
            try {
                streamer.serialize(root);
            } catch (final TransformerException e) {
                LOG.error(e.getMessageAndLocation());
                throw new ServletException("Error while serializing result: " + e.getMessage(), e);
            }
            writer.flush();
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        client = new JMXtoXML();
        client.connect();

        registerLocalHostAddresses();
        createTokenFile();
    }

    /**
     * Register all known IP-addresses for localhost
     */
    void registerLocalHostAddresses() {
        // The external IP address of the server
        try {
            localhostAddresses.add(InetAddress.getLocalHost().getHostAddress());
        } catch (UnknownHostException ex) {
            LOG.warn(String.format("Unable to get HostAddress for LocalHost: %s", ex.getMessage()));
        }

        // The configured Localhost addresses
        try {
            for (InetAddress address : InetAddress.getAllByName("localhost")) {
                localhostAddresses.add(address.getHostAddress());
            }
        } catch (UnknownHostException ex) {
            LOG.warn(String.format("Unable to retrieve ipaddresses for LocalHost: %s", ex.getMessage()));
        }

        if (localhostAddresses.isEmpty()) {
            LOG.error("Unable to determine addresses for localhost, jmx servlet is disfunctional.");
        }
    }

    /**
     * Determine if HTTP request is originated from localhost.
     *
     * @param request The HTTP request
     * @return TRUE if request is from LOCALHOST otherwise FALSE
     */
    boolean isFromLocalHost(HttpServletRequest request) {
        return localhostAddresses.contains(request.getRemoteAddr());
    }

    /**
     * Check if URL contains magic Token
     *
     * @param request The HTTP request
     * @return TRUE if request contains correct value for token, else FALSE
     */
    boolean hasSecretToken(HttpServletRequest request, String token) {
        String[] tokenValue = request.getParameterValues("token");
        return ArrayUtils.contains(tokenValue, token);
    }

    /**
     * Create reference to token file
     */
    private void createTokenFile() {

        if (tokenFile == null) {
            File existHome = ConfigurationHelper.getExistHome();
            File dataDir = new File(existHome, "webapp/WEB-INF/data");
            tokenFile = (dataDir.exists()) ? new File(dataDir, "jmxservlet.token") : new File(existHome, "jmxservlet.token");
        }
    }

    /**
     * Get token from file, create if not existent
     */
    private String createGetToken() {

        Properties props = new Properties();
        String token = null;

        // Read if possible
        if (tokenFile.exists()) {
            InputStream is = null;
            try {
                is = new FileInputStream(tokenFile);
                props.load(is);

                token = props.getProperty("token");
            } catch (IOException ex) {
                LOG.error(ex.getMessage());
            } finally {
                IOUtils.closeQuietly(is);
            }

        }

        // Create and write when needed
        if (!tokenFile.exists() || token == null) {
            token = UUID.randomUUID().toString();

            props.setProperty("token", token);

            OutputStream os = null;
            try {
                os = new FileOutputStream(tokenFile);
                props.store(os, "");

            } catch (IOException ex) {
                LOG.error(ex.getMessage());
            } finally {
                IOUtils.closeQuietly(os);
            }

        }

        return token;
    }

}
