package org.exist.xquery.modules.xslfo;

import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.apache.fop.apps.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.storage.DBBroker;
import org.exist.xquery.value.NodeValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class ApacheFopProcessorAdapter implements ProcessorAdapter {

    private static final Logger LOG = LogManager.getLogger(ApacheFopProcessorAdapter.class);

    @Override
    public ContentHandler getContentHandler(final DBBroker broker, final NodeValue configFile, final Properties parameters, final String mimeType, final OutputStream os) throws SAXException {

        // setup the FopFactory
        final FopFactoryBuilder builder;
        try {
            if (configFile != null) {
                final FopConfigurationBuilder cfgBuilder = new FopConfigurationBuilder(broker);
                final Configuration cfg = cfgBuilder.buildFromNode(configFile);
                final URI defaultBaseURI = new URI(configFile.getOwnerDocument().getBaseURI());
                final EnvironmentProfile environment = EnvironmentalProfileFactory.createDefault(defaultBaseURI, null);
                builder = new FopFactoryBuilder(environment).setConfiguration(cfg);
            } else {
                builder = new FopFactoryBuilder(new URI("file:///db"));
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
     * Extension of the Apache Avalon DefaultConfigurationBuilder Allows better
     * integration with Nodes passed in from eXist as Configuration files
     */
    private class FopConfigurationBuilder extends org.apache.avalon.framework.configuration.DefaultConfigurationBuilder {

        private DBBroker broker = null;

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
