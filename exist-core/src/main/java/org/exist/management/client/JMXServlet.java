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
package org.exist.management.client;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.util.serializer.DOMSerializer;
import org.w3c.dom.Element;

import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A servlet to monitor the database. It returns status information for the database based on the JMX interface. For
 * simplicity, the JMX beans provided by eXist are organized into categories. One calls the servlet with one or more
 * categories in parameter "c", e.g.:
 * <p>
 * /exist/jmx?c=instances&amp;c=memory
 * <p>
 * If no parameter is specified, all categories will be returned. Valid categories are "memory", "instances", "disk",
 * "system", "caches", "locking", "processes", "sanity", "vector", "all".
 * <p>
 * The servlet can also be used to test if the database is responsive by using parameter "operation=ping" and a timeout
 * (t=timeout-in-milliseconds). For example, the following call
 * <p>
 * /exist/jmx?operation=ping&amp;t=1000
 * <p>
 * will wait for a response within 1000ms. If the ping returns within the specified timeout, the servlet returns the
 * attributes of the SanityReport JMX bean, which will include an element &lt;jmx:Status&gt;PING_OK&lt;/jmx:Status&gt;.
 * If the ping takes longer than the timeout, you'll instead find an element &lt;jmx:error&gt; in the returned XML. In
 * this case, additional information on running queries, memory consumption and database locks will be provided.
 * <p>
 *
 * @author wolf
 */
public class JMXServlet extends HttpServlet {

    protected final static Logger LOG = LogManager.getLogger(JMXServlet.class);

    private static final String TOKEN_KEY = "token";
    private static final String WEBINF_DATA_DIR = "WEB-INF/data";

    private final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
    }

    private final Set<String> localhostAddresses = new HashSet<>();
    private JMXtoXML client;
    private JMXTokenProvider tokenProvider;

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {

        // Verify if request is from localhost or if user has specific servlet/container managed role.
        if (isFromLocalHost(request)) {
            // Localhost is always authorized to access
            LOG.debug("Local access granted");

        } else if (hasSecretToken(request, tokenProvider.getToken().orElse(null))) {
            // Correct token is provided
            LOG.debug("Correct token provided by {}", request.getRemoteHost());

        } else {
            // Check if user is already authorized, e.g. via MONEX allow user too
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access allowed for localhost, or when correct token has been provided.");
            return;
        }

        // Perform actual writing of data
        writeXmlData(request, response);
    }

    private void writeXmlData(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        final Element root;

        final String operation = request.getParameter("operation");
        if ("ping".equals(operation)) {
            long timeout = 5000;
            final String timeoutParam = request.getParameter("t");
            if (timeoutParam != null && !timeoutParam.isBlank()) {
                try {
                    timeout = Long.parseLong(timeoutParam);
                } catch (final NumberFormatException e) {
                    throw new ServletException("timeout parameter needs to be a number. Got: " + timeoutParam);
                }
            }

            final long responseTime = client.ping(BrokerPool.DEFAULT_INSTANCE_NAME, timeout);
            if (responseTime == JMXtoXML.PING_TIMEOUT) {
                root = client.generateXMLReport("no response on ping after %sms".formatted(timeout),
                        new String[]{"sanity", "locking", "processes", "instances", "memory"});
            } else {
                root = client.generateXMLReport(null, new String[]{"sanity"});
            }
        } else if (operation != null && !operation.isEmpty()) {
            final String mbean = request.getParameter("mbean");
            if (mbean == null) {
                throw new ServletException("to call an operation, you also need to specify parameter 'mbean'");
            }
            final String[] args = request.getParameterValues("args");
            try {
                root = client.invoke(mbean, operation, args);
                if (root == null) {
                    throw new ServletException("operation " + operation + " not found on " + mbean);
                }
            } catch (final InstanceNotFoundException e) {
                throw new ServletException("mbean " + mbean + " not found: " + e.getMessage(), e);
            } catch (final MalformedObjectNameException | IntrospectionException | ReflectionException |
                           MBeanException e) {
                throw new ServletException(e.getMessage(), e);
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
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
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
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        // Setup JMS client
        client = new JMXtoXML();
        client.connect();

        // Register all known localhost addresses
        registerLocalHostAddresses();

        // Resolve the token, backed by the DiskUsage MBean's DataDirectory, falling back to
        // WEB-INF/data if that MBean attribute is unavailable. getRealPath() can return null
        // (e.g. when the webapp isn't unpacked to disk), in which case there is no fallback.
        final String realPath = config.getServletContext().getRealPath(WEBINF_DATA_DIR);
        final Path fallbackDataDir = realPath != null ? Path.of(realPath).normalize() : null;
        tokenProvider = new JMXTokenProvider(client, fallbackDataDir);

        LOG.info("JMXservlet token: {}", tokenProvider.getToken().orElse(null));

    }

    /**
     * Register all known IP-addresses for localhost.
     */
    void registerLocalHostAddresses() {

        try {
            final Predicate<NetworkInterface> isLoopback = networkInterface -> {
                try {
                    return networkInterface.isLoopback();
                } catch (final SocketException e) {
                    LOG.error("Unable to determine if {} is a loopback interface", e.getMessage(), e);
                    return false;
                }
            };

            NetworkInterface.networkInterfaces()
                    .filter(isLoopback)
                    .flatMap(NetworkInterface::inetAddresses)
                    .map(InetAddress::getHostAddress)
                    .map(a -> a.replaceFirst("%.+", ""))
                    .forEach(localhostAddresses::add);

        } catch (final SocketException e) {
            LOG.error("Unable to determine localhost loopback addresses: {}  ", e.getMessage(), e);
        }

        if (localhostAddresses.isEmpty()) {
            LOG.error("Unable to determine addresses for localhost, jmx servlet might be dysfunctional.");
        } else {
            LOG.info("Detected loopback addresses: {}", localhostAddresses);
        }
    }

    /**
     * Determine if HTTP request is originated from localhost.
     *
     * @param request The HTTP request
     * @return TRUE if request is from LOCALHOST otherwise FALSE
     */
    boolean isFromLocalHost(final HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        remoteAddr = remoteAddr.startsWith("[") ? remoteAddr.substring(1, remoteAddr.length() - 1) : remoteAddr;
        return localhostAddresses.contains(remoteAddr);
    }

    /**
     * Check if URL contains magic Token
     *
     * @param request The HTTP request
     * @return TRUE if request contains correct value for token, else FALSE
     */
    boolean hasSecretToken(final HttpServletRequest request, final String token) {
        if (token == null || token.isBlank()) {
            // Refuse to authenticate against an empty or whitespace-only token,
            // even if the request supplied a matching empty value.
            return false;
        }
        final String[] tokenValue = request.getParameterValues(TOKEN_KEY);
        return ArrayUtils.contains(tokenValue, token);
    }

}
