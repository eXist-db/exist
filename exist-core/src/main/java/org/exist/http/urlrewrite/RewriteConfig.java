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
package org.exist.http.urlrewrite;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.thirdparty.net.sf.saxon.functions.regex.JDK15RegexTranslator;
import org.exist.thirdparty.net.sf.saxon.functions.regex.RegexSyntaxException;
import org.exist.thirdparty.net.sf.saxon.functions.regex.RegularExpression;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles static mapping configuration for the @link XQueryURLRewrite filter,
 * defined in controller-config.xml. The static mapping is used to map
 * base paths to base controllers or servlets.
 */
public class RewriteConfig {
    private static final Logger LOG = LogManager.getLogger(RewriteConfig.class);

    public static final String CONFIG_FILE = "controller-config.xml";
    public static final String PATTERN_ATTRIBUTE = "pattern";
    /**
     * Adding server-name="www.example.com" to a root tag in the controller-config.xml file.
     *
     * i.e.
     *
     * &lt;root server-name="example1.com" pattern="/*" path="xmldb:exist:///db/org/example1/"/&gt;
     * &lt;root server-name="example2.com" pattern="/*" path="xmldb:exist:///db/org/example2/"/&gt;
     *
     * Will redirect http://example1.com to /db/org/example1/
     * and http://example2.com to /db/org/example2/
     *
     * If there is no server-name attribute on the root tag, then the server name is ignored while performing the URL rewriting.
     */
    public static final String SERVER_NAME_ATTRIBUTE = "server-name";

    // the list of established mappings
    private final List<Mapping> mappings = new ArrayList<>();

    // parent XQueryURLRewrite
    private final XQueryURLRewrite urlRewrite;

    public RewriteConfig(final XQueryURLRewrite urlRewrite) throws ServletException {
        this.urlRewrite = urlRewrite;
        String controllerConfig = urlRewrite.getConfig().getInitParameter("config");
        if (controllerConfig == null) {
            controllerConfig = CONFIG_FILE;
        }

        configure(controllerConfig);
    }

    /**
     * Lookup the given path in the static mappings table.
     *
     * @param request use the path from this request
     * @return the URLRewrite instance for the mapping or null if none was found
     */
    public synchronized URLRewrite lookup(final HttpServletRequest request) {
        final String path = request.getRequestURI().substring(request.getContextPath().length());
        return lookup(path, request.getServerName(), false, null);
    }

    /**
     * Lookup the given path in the static mappings table.
     *
     * @param path the path to look up
     * @param serverName the servers name
     * @param staticMapping don't return redirects to other controllers, just static mappings to servlets.
     * @param copyFrom the urlrewrite rule to copy from or null
     *
     * @return the URLRewrite instance for the mapping or null if none was found
     */
    public synchronized URLRewrite lookup(String path, final String serverName, final boolean staticMapping, final URLRewrite copyFrom) {
        final int p = path.lastIndexOf(';');
        if (p != Constants.STRING_NOT_FOUND) {
            path = path.substring(0, p);
        }
        for (final Mapping mapping : mappings) {
            final String matchedString = mapping.match(path);
            if (matchedString != null) {
                final URLRewrite action = mapping.action.copy();
                if (copyFrom != null) {
                    action.copyFrom(copyFrom);
                }

                /*
                 * If the URLRewrite is a ControllerForward, then test to see if there is a condition
                 * on the server name.  If there is a condition on the server name and the names do not
                 * match, then ignore this ControllerForward.
                 */
                if (action instanceof ControllerForward) {
                    if (serverName != null) {
                        final String controllerServerName = ((ControllerForward) action).getServerName();
                        if (controllerServerName != null) {
                            if (!serverName.equalsIgnoreCase(controllerServerName)) {
                                continue;
                            }
                        }
                    }

                }
                // if the mapping matches a part of the URI only, set the prefix to the
                // matched string. This will later be stripped from the URI.
                if (matchedString.length() != path.length() && !"/".equals(matchedString)) {
                    action.setPrefix(matchedString);
                }
                action.setURI(path);
                if (!staticMapping || !(action instanceof ControllerForward)) {
                    return action;
                }
            }
        }
        return null;
    }

    private void configure(final String controllerConfig) throws ServletException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loading XQueryURLRewrite configuration from {}", controllerConfig);
        }

        if (controllerConfig.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {

            try (final DBBroker broker = urlRewrite.getBrokerPool().get(Optional.ofNullable(urlRewrite.getDefaultUser()))) {
                try (final LockedDocument lockedDocument = broker.getXMLResource(XmldbURI.create(controllerConfig), LockMode.READ_LOCK)) {
                    final DocumentImpl doc = lockedDocument == null ? null : lockedDocument.getDocument();
                    if (doc != null) {
                        parse(doc);
                    }
                }
            } catch (final EXistException | PermissionDeniedException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            }
        } else {
            try {
                final Path d = Paths.get(urlRewrite.getConfig().getServletContext().getRealPath("/")).normalize();
                final Path configFile = d.resolve(controllerConfig);
                if (Files.isReadable(configFile)) {
                    final Document doc = parseConfig(configFile);
                    parse(doc);
                }
            } catch (final ParserConfigurationException | IOException | SAXException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            }
        }
        urlRewrite.clearCaches();
    }

    private void parse(final Document doc) throws ServletException {
        final Element root = doc.getDocumentElement();
        Node child = root.getFirstChild();
        while (child != null) {
            final String ns = child.getNamespaceURI();
            if (child.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(ns)) {
                final Element elem = (Element) child;
                final String pattern = elem.getAttribute(PATTERN_ATTRIBUTE);
                if (pattern == null) {
                    throw new ServletException("Action in controller-config.xml has no pattern: " + elem.toString());
                }
                final URLRewrite urw = parseAction(urlRewrite.getConfig(), pattern, elem);
                if (urw == null) {
                    throw new ServletException("Unknown action in controller-config.xml: " + elem.getNodeName());
                }
                mappings.add(new Mapping(pattern, urw));
            }
            child = child.getNextSibling();
        }
    }

    private URLRewrite parseAction(final ServletConfig config, final String pattern, final Element action) throws ServletException {
        final URLRewrite rewrite;
        switch (action.getLocalName()) {
            case "forward" -> rewrite = new PathForward(config, action, pattern);
            case "redirect" -> rewrite = new Redirect(action, pattern);
            case "root" -> {
                final ControllerForward cf = new ControllerForward(action, pattern);

                /*
                 * If there is a server-name attribute on the root tag, then add that
                 * as an attribute on the ControllerForward object.
                 */
                final String serverName = action.getAttribute(SERVER_NAME_ATTRIBUTE);
                if (serverName != null && serverName.length() > 0) {
                    cf.setServerName(serverName);
                }
                rewrite = cf;
            }
            case null, default -> rewrite = null;
        }
        return rewrite;
    }

    private Document parseConfig(final Path file) throws ParserConfigurationException, SAXException, IOException {
        try (final InputStream is = new BufferedInputStream(Files.newInputStream(file))) {
            final InputSource src = new InputSource(is);
            final XMLReaderPool parserPool = urlRewrite.getBrokerPool().getParserPool();
            XMLReader xr = null;
            try {
                xr = parserPool.borrowXMLReader();
                final SAXAdapter adapter = new SAXAdapter((Expression) null);
                xr.setContentHandler(adapter);
                xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
                xr.parse(src);
                return adapter.getDocument();
            } finally {
                if (xr != null) {
                    parserPool.returnXMLReader(xr);
                }
            }
        }
    }

    /**
     * Maps a regular expression to an URLRewrite instance
     */
    private static final class Mapping {
        private final Pattern pattern;
        private final URLRewrite action;
        private Matcher matcher;

        private Mapping(String regex, final URLRewrite action) throws ServletException {
            try {
                final int options = RegularExpression.XML11 | RegularExpression.XPATH30;
                int flagbits = 0;

                final List<RegexSyntaxException> warnings = new ArrayList<>();
                regex = JDK15RegexTranslator.translate(regex, options, flagbits, warnings);

                this.pattern = Pattern.compile(regex, 0);
                this.action = action;
                this.matcher = pattern.matcher("");
            } catch (final RegexSyntaxException e) {
                throw new ServletException("Syntax error in regular expression specified for path. " +
                        e.getMessage(), e);
            }
        }

        public String match(final String path) {
            matcher.reset(path);
            if (matcher.lookingAt()) {
                return path.substring(matcher.start(), matcher.end());
            }
            return null;
        }
    }
}
