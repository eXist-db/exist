package org.exist.xquery.modules.xslfo;

import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.apache.fop.apps.*;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.apache.xmlgraphics.io.URIResolverAdapter;
import org.exist.storage.DBBroker;
import org.exist.xslt.EXistURIResolver;
import org.exist.xquery.value.NodeValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;


/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class ApacheFopProcessorAdapter implements ProcessorAdapter {

    private static final Logger LOG = LogManager.getLogger(ApacheFopProcessorAdapter.class);
    private static final String DEFAULT_BASE_URI = "exist://localhost/db/";

    @Override
    public ContentHandler getContentHandler(final DBBroker broker, final NodeValue configFile, final Properties parameters, final String mimeType, final OutputStream os) throws SAXException {

        // setup the FopFactory
        final FopFactoryBuilder builder;
        try {
            if (configFile != null) {
                final FopConfigurationBuilder cfgBuilder = new FopConfigurationBuilder(broker);
                final Configuration cfg = cfgBuilder.buildFromNode(configFile);
                final URI defaultBaseURI;
                if(configFile instanceof org.exist.dom.memtree.NodeImpl) {
                    //in-memory documents don't have a BaseURI
                    defaultBaseURI = new URI(DEFAULT_BASE_URI);
                } else {
                    defaultBaseURI = new URI(configFile.getOwnerDocument().getBaseURI());
                }
                final EnvironmentProfile environment = EnvironmentalProfileFactory.createDefault(defaultBaseURI, getResourceResolver(broker, defaultBaseURI.toString()));
                builder = new FopFactoryBuilder(environment).setConfiguration(cfg);
            } else {
                final URI defaultBaseURI = new URI(DEFAULT_BASE_URI);
                final EnvironmentProfile environment = EnvironmentalProfileFactory.createDefault(defaultBaseURI, getResourceResolver(broker, defaultBaseURI.toString()));
                builder = new FopFactoryBuilder(environment);
            }

            final FopFactory fopFactory = builder.build();

            // setup the foUserAgent, using given parameters held in the
            // transformer handler
            final FOUserAgent foUserAgent = setupFOUserAgent(fopFactory.newFOUserAgent(), parameters);

            // create new instance of FOP using the mimetype, the created user
            // agent, and the output stream
            final Fop fop = fopFactory.newFop(mimeType, foUserAgent, os);

            // Obtain FOP's DefaultHandler
            return fop.getDefaultHandler();
        } catch(final URISyntaxException e) {
            throw new SAXException("Unable to parse baseURI", e);
        }
    }

    @Override
    public void cleanup() {
    }

    /**
     * Setup the UserAgent for FOP, from given parameters *
     *
     * @param foUserAgent The user agent to set parameters for
     * @param parameters
     *            any user defined parameters to the XSL-FO process
     * @return FOUserAgent The generated FOUserAgent to include any parameters
     *         passed in
     */
    private FOUserAgent setupFOUserAgent(final FOUserAgent foUserAgent, final Properties parameters) {

        // setup the foUserAgent as per the parameters given
        foUserAgent.setProducer("eXist-db with Apache FOP");

        if(parameters != null) {
            for(final Entry paramEntry : parameters.entrySet()) {
                final String key = (String)paramEntry.getKey();
                final String value = (String)paramEntry.getValue();

                if(key.equals("FOPauthor")) {
                    foUserAgent.setAuthor(value);
                } else if(key.equals("FOPtitle")) {
                    foUserAgent.setTitle(value);
                } else if(key.equals("FOPkeywords")) {
                    foUserAgent.setTitle(value);
                } else if(key.equals("FOPdpi")) {
                    try {
                        foUserAgent.setTargetResolution(Integer.parseInt(value));
                    } catch(final NumberFormatException nfe) {
                        LOG.warn("Unable to set DPI to: " + value);
                    }
                }
            }
        }

        return foUserAgent;
    }

    /**
     * Returns a scheme aware ResourceResolver which supports:
     *   file://
     *   exist:// (which will be translated to xmldb:exist://)
     *   http://
     *   https://
     *
     * @return The resource resolver
     */
    private ResourceResolver getResourceResolver(final DBBroker broker, final String baseUri) {
        final ResourceResolverFactory.SchemeAwareResourceResolverBuilder builder = ResourceResolverFactory.createSchemeAwareResourceResolverBuilder(ResourceResolverFactory.createDefaultResourceResolver());
        final URIResolverAdapter uriResolver = new URIResolverAdapter(
            new ExistSchemeRewriter(new EXistURIResolver(broker.getBrokerPool(), baseUri))
        );
        builder.registerResourceResolverForScheme("exist", uriResolver);
        builder.registerResourceResolverForScheme("http", uriResolver);
        builder.registerResourceResolverForScheme("https", uriResolver);
        return builder.build();
    }

    /**
     * Rewrites URLs like:
     *  exist://localhost/db -> /db
     */
    private static class ExistSchemeRewriter implements URIResolver {
        private final EXistURIResolver eXistURIResolver;

        public ExistSchemeRewriter(final EXistURIResolver eXistURIResolver) {
            this.eXistURIResolver = eXistURIResolver;
        }

        @Override
        public Source resolve(final String href, final String base) throws TransformerException {
            return eXistURIResolver.resolve(
                    rewriteScheme(href),
                    rewriteScheme(base)
            );
        }

        private String rewriteScheme(String uri) {
            if(uri != null) {
                if (uri.startsWith("exist://localhost")) {
                    uri = uri.replace("exist://localhost/db", "/db");
                } else if (uri.startsWith("exist://")) {
                    uri = uri.replace("exist://", "xmldb:exist://");
                }
            }

            return uri;
        }
    }

    /**
     * Extension of the Apache Avalon DefaultConfigurationBuilder Allows better
     * integration with Nodes passed in from eXist as Configuration files
     */
    private class FopConfigurationBuilder extends org.apache.avalon.framework.configuration.DefaultConfigurationBuilder {

        private final DBBroker broker;

        public FopConfigurationBuilder(final DBBroker broker) {
            super();
            this.broker = broker;
        }

        @SuppressWarnings("unused")
        public FopConfigurationBuilder(final DBBroker broker, final boolean enableNamespaces) {
            super(enableNamespaces);
            this.broker = broker;
        }

        public Configuration buildFromNode(final NodeValue configFile) throws SAXException {
            final SAXConfigurationHandler handler = getHandler();
            handler.clear();
            configFile.toSAX(broker, handler, new Properties());
            return handler.getConfiguration();
        }
    }
}
