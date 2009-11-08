package org.exist.http.urlrewrite;

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.dom.DocumentImpl;
import org.exist.xmldb.XmldbURI;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.memtree.SAXAdapter;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.Constants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles static mapping configuration for the @link XQueryURLRewrite filter,
 * defined in controller-config.xml. The static mapping is used to map
 * base paths to base controllers or servlets.
 */
public class RewriteConfig {

    public final static String CONFIG_FILE = "controller-config.xml";

    public final static String MAP_ELEMENT = "map";
    public final static String PATTERN_ATTRIBUTE = "pattern";

    /**
     * Maps a regular expression to an URLRewrite instance
     */
    private final static class Mapping {

        Pattern pattern;
        Matcher matcher = null;
        URLRewrite action;

        private Mapping(String regex, URLRewrite action) throws ServletException {
            try {
                regex = RegexTranslator.translate(regex, true);
                this.pattern = Pattern.compile(regex, 0);
                this.action = action;
            } catch (RegexTranslator.RegexSyntaxException e) {
                throw new ServletException("Syntax error in regular expression specified for path. " +
                    e.getMessage(), e);
            }
        }

        public String match(String path) {
            if (matcher == null)
                matcher = pattern.matcher(path);
            else
                matcher.reset(path);
            if (matcher.lookingAt()) {
                return path.substring(matcher.start(), matcher.end());
            }
            return null;
        }
    }

    private final static Logger LOG = Logger.getLogger(RewriteConfig.class);

    // the list of established mappings
    private List<Mapping> mappings = new ArrayList<Mapping>();

    // parent XQueryURLRewrite
    private XQueryURLRewrite urlRewrite;

    public RewriteConfig(XQueryURLRewrite urlRewrite) throws ServletException {
        this.urlRewrite = urlRewrite;
        String controllerConfig = urlRewrite.getConfig().getInitParameter("config");
        if (controllerConfig == null)
            controllerConfig = CONFIG_FILE;

        configure(controllerConfig);
    }

    /**
     * Lookup the given path in the static mappings table.
     *
     * @param request use the path from this request
     * @return the URLRewrite instance for the mapping or null if none was found
     * @throws ServletException
     */
    public synchronized URLRewrite lookup(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return lookup(path, false);
    }

    /**
     * Lookup the given path in the static mappings table.
     *
     * @param path path to look up
     * @param staticMapping don't return redirects to other controllers, just static mappings
     *  to servlets.
     * @return the URLRewrite instance for the mapping or null if none was found
     * @throws ServletException
     */
    public synchronized URLRewrite lookup(String path, boolean staticMapping) throws ServletException {
        int p = path.lastIndexOf(';');
        if(p != Constants.STRING_NOT_FOUND)
            path = path.substring(0, p);
        for (int i = 0; i < mappings.size(); i++) {
            Mapping m = mappings.get(i);
            String matchedString = m.match(path);
            if (matchedString != null) {
                URLRewrite action = m.action;
                // if the mapping matches a part of the URI only, set the prefix to the
                // matched string. This will later be stripped from the URI.
                if (matchedString.length() != path.length() && !matchedString.equals("/"))
                    action.setPrefix(matchedString);
                action.setURI(path);
                if (!staticMapping || !(action instanceof ControllerForward))
                    return action;
            }
        }
        return null;
    }

    private void configure(String controllerConfig) throws ServletException {
        LOG.debug("Loading XQueryURLRewrite configuration from " + controllerConfig);
        if (controllerConfig.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            DBBroker broker = null;
            DocumentImpl doc = null;
            try {
                broker = urlRewrite.pool.get(urlRewrite.user);

                doc = broker.getXMLResource(XmldbURI.create(controllerConfig), Lock.READ_LOCK);
                if (doc != null)
                    parse(doc);
            } catch (EXistException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            } catch (PermissionDeniedException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            } finally {
                if (doc != null)
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                urlRewrite.pool.release(broker);
            }
        } else {
            try {
                File d = new File(urlRewrite.getConfig().getServletContext().getRealPath("."));
                File configFile = new File(d, controllerConfig);
                if (configFile.canRead()) {
                    Document doc = parseConfig(configFile);
                    parse(doc);
                }
            } catch (ParserConfigurationException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            } catch (SAXException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new ServletException("Failed to parse controller.xml: " + e.getMessage(), e);
            }
        }
        urlRewrite.clearCaches();
    }

    private void parse(Document doc) throws ServletException {
        Element root = doc.getDocumentElement();
        Node child = root.getFirstChild();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE &&
                child.getNamespaceURI().equals(Namespaces.EXIST_NS)) {
                Element elem = (Element) child;
                String pattern = elem.getAttribute(PATTERN_ATTRIBUTE);
                if (pattern == null)
                    throw new ServletException("Action in controller-config.xml has no pattern: " + elem.toString());
                URLRewrite urw = parseAction(urlRewrite.getConfig(), pattern, elem);
                if (urw == null)
                    throw new ServletException("Unknown action in controller-config.xml: " + elem.getNodeName());
                mappings.add(new Mapping(pattern, urw));
            }
            child = child.getNextSibling();
        }
    }

    private URLRewrite parseAction(FilterConfig config, String pattern, Element action) throws ServletException {
        URLRewrite rewrite = null;
        if ("forward".equals(action.getLocalName())) {
            rewrite = new PathForward(config, action, pattern);
        } else if ("redirect".equals(action.getLocalName())) {
            rewrite = new Redirect(action, pattern);
        } else if ("root".equals(action.getLocalName())) {
            rewrite = new ControllerForward(action, pattern);
        }
        return rewrite;
    }

    private Document parseConfig(File file) throws ParserConfigurationException, SAXException, IOException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(new FileInputStream(file));
        SAXParser parser = factory.newSAXParser();
        XMLReader xr = parser.getXMLReader();
        SAXAdapter adapter = new SAXAdapter();
        xr.setContentHandler(adapter);
        xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
        xr.parse(src);

        return adapter.getDocument();
    }
}