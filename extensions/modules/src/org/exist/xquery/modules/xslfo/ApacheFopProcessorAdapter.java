package org.exist.xquery.modules.xslfo;

import java.io.OutputStream;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
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

    private final FopFactory fopFactory = FopFactory.newInstance();

    @Override
    public ContentHandler getContentHandler(DBBroker broker, NodeValue configFile, Properties parameters, String mimeType, OutputStream os) throws SAXException {

        // setup the FopFactory
        if(configFile != null) {
            FopConfigurationBuilder cfgBuilder = new FopConfigurationBuilder(broker);
            Configuration cfg = cfgBuilder.buildFromNode(configFile);
            fopFactory.setUserConfig(cfg);
        }

        // setup the foUserAgent, using given parameters held in the
        // transformer handler
        FOUserAgent foUserAgent = setupFOUserAgent(fopFactory.newFOUserAgent(), parameters);

        // create new instance of FOP using the mimetype, the created user
        // agent, and the output stream
        Fop fop = fopFactory.newFop(mimeType, foUserAgent, os);
        
        // Obtain FOP's DefaultHandler
        return fop.getDefaultHandler();
    }

    @Override
    public void cleanup() {
    }

    /**
     * Setup the UserAgent for FOP, from given parameters *
     *
     * @param transformer
     *            Created based on the XSLT, so containing any parameters to the
     *            XSL-FO specified in the XQuery
     * @param parameters
     *            any user defined parameters to the XSL-FO process
     * @return FOUserAgent The generated FOUserAgent to include any parameters
     *         passed in
     */
    private FOUserAgent setupFOUserAgent(FOUserAgent foUserAgent, Properties parameters) {

        // setup the foUserAgent as per the parameters given
        foUserAgent.setProducer("eXist-db with Apache FOP");

        if(parameters != null) {
            for(Entry paramEntry : parameters.entrySet()) {
                String key = (String)paramEntry.getKey();
                String value = (String)paramEntry.getValue();

                if(key.equals("FOPauthor")) {
                    foUserAgent.setAuthor(value);
                } else if(key.equals("FOPtitle")) {
                    foUserAgent.setTitle(value);
                } else if(key.equals("FOPkeywords")) {
                    foUserAgent.setTitle(value);
                } else if(key.equals("FOPdpi")) {
                    try {
                        foUserAgent.setTargetResolution(Integer.parseInt(value));
                    } catch(NumberFormatException nfe) {
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

        DBBroker broker = null;

        public FopConfigurationBuilder(DBBroker broker) {
            super();
            this.broker = broker;
        }

        @SuppressWarnings("unused")
        public FopConfigurationBuilder(DBBroker broker, final boolean enableNamespaces) {
            super(enableNamespaces);
            this.broker = broker;
        }

        public Configuration buildFromNode(NodeValue configFile) throws SAXException {
            SAXConfigurationHandler handler = getHandler();
            handler.clear();
            configFile.toSAX(broker, handler, new Properties());
            return handler.getConfiguration();
        }
    }
}
