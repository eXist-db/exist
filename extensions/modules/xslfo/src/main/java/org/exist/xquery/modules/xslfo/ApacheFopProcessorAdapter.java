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
package org.exist.xquery.modules.xslfo;

import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.SAXConfigurationHandler;
import org.apache.fop.apps.*;
import org.apache.fop.apps.io.ResourceResolverFactory;
import org.apache.fop.configuration.ConfigurationException;
import org.apache.fop.events.Event;
import org.apache.fop.events.EventFormatter;
import org.apache.fop.events.EventListener;
import org.apache.fop.events.model.EventSeverity;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.xmlgraphics.io.ResourceResolver;
import org.apache.xmlgraphics.io.URIResolverAdapter;
import org.exist.storage.DBBroker;
import org.exist.util.EXistURISchemeURIResolver;
import org.exist.xquery.value.NodeValue;
import org.exist.xslt.EXistURIResolver;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;


/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class ApacheFopProcessorAdapter implements ProcessorAdapter {

    private static final Logger LOG = LogManager.getLogger(ApacheFopProcessorAdapter.class);
    private static final String DEFAULT_BASE_URI = "exist://localhost/db/";

    @Override
    public ContentHandler getContentHandler(final DBBroker broker, final NodeValue processorConfig, final Properties parameters, final String mediaType, final OutputStream os) throws SAXException {

        // setup the FopFactory
        final FopFactoryBuilder builder;
        try {
            if (processorConfig != null) {
                final FopConfigurationBuilder cfgBuilder = new FopConfigurationBuilder(broker);
                final Configuration cfg = cfgBuilder.buildFromNode(processorConfig);
                final URI defaultBaseURI;
                if (processorConfig instanceof org.exist.dom.memtree.NodeImpl) {
                    //in-memory documents don't have a BaseURI
                    defaultBaseURI = new URI(DEFAULT_BASE_URI);
                } else {
                    defaultBaseURI = new URI("exist://localhost" + processorConfig.getOwnerDocument().getBaseURI());
                }
                final EnvironmentProfile environment = EnvironmentalProfileFactory.createDefault(defaultBaseURI, getResourceResolver(broker, defaultBaseURI.toString()));
                builder = new FopFactoryBuilder(environment).setConfiguration(new FopAvalonConfigurationAdapter(cfg));
            } else {
                final URI defaultBaseURI = new URI(DEFAULT_BASE_URI);
                final EnvironmentProfile environment = EnvironmentalProfileFactory.createDefault(defaultBaseURI, getResourceResolver(broker, defaultBaseURI.toString()));
                builder = new FopFactoryBuilder(environment);
            }

            final FopFactory fopFactory = builder.build();

            // setup the foUserAgent, using given parameters held in the
            // transformer handler
            final FOUserAgent foUserAgent = setupFOUserAgent(fopFactory.newFOUserAgent(), parameters);

            foUserAgent.getEventBroadcaster().addEventListener(new ExistLoggingEventListener(LOG));

            // create new instance of FOP using the mimetype, the created user
            // agent, and the output stream
            final Fop fop = fopFactory.newFop(mediaType, foUserAgent, os);
            // Obtain FOP's DefaultHandler
            return fop.getDefaultHandler();
        } catch (final URISyntaxException e) {
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
     * @param parameters  any user defined parameters to the XSL-FO process
     * @return FOUserAgent The generated FOUserAgent to include any parameters
     * passed in
     */
    private FOUserAgent setupFOUserAgent(final FOUserAgent foUserAgent, final Properties parameters) {

        // setup the foUserAgent as per the parameters given
        foUserAgent.setProducer("eXist-db with Apache FOP");

        if (parameters != null) {
            for (final Entry paramEntry : parameters.entrySet()) {
                final String key = (String) paramEntry.getKey();
                final String value = (String) paramEntry.getValue();

                switch (key) {
                    case "FOPauthor":
                        foUserAgent.setAuthor(value);
                        break;
                    case "FOPtitle":
                        foUserAgent.setTitle(value);
                        break;
                    case "FOPkeywords":
                        foUserAgent.setTitle(value);
                        break;
                    case "FOPdpi":
                        try {
                            foUserAgent.setTargetResolution(Integer.parseInt(value));
                        } catch (final NumberFormatException nfe) {
                            LOG.warn("Unable to set DPI to: {}", value);
                        }
                        break;
                }
            }
        }

        return foUserAgent;
    }

    /**
     * Returns a scheme aware ResourceResolver which supports:
     * file://
     * exist:// (which will be translated to xmldb:exist://)
     * http://
     * https://
     *
     * @return The resource resolver
     */
    private ResourceResolver getResourceResolver(final DBBroker broker, final String baseUri) {
        final ResourceResolverFactory.SchemeAwareResourceResolverBuilder builder = ResourceResolverFactory.createSchemeAwareResourceResolverBuilder(ResourceResolverFactory.createDefaultResourceResolver());
        final URIResolverAdapter uriResolver = new URIResolverAdapter(
                new EXistURISchemeURIResolver(new EXistURIResolver(broker.getBrokerPool(), baseUri))
        );
        builder.registerResourceResolverForScheme("exist", uriResolver);
        builder.registerResourceResolverForScheme("http", uriResolver);
        builder.registerResourceResolverForScheme("https", uriResolver);
        return builder.build();
    }

    /**
     * Extension of the Apache Avalon DefaultConfigurationBuilder Allows better
     * integration with Nodes passed in from eXist as Configuration files
     */
    private static class FopConfigurationBuilder
            extends org.apache.avalon.framework.configuration.DefaultConfigurationBuilder {

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

    /**
         * Adapter between Avalon config which lets us easily use a Node as the configuration
         * source, and Apache Fop configuration which does not.
         * <p>
         * This was needed after the Fop API changed from version 2.3 to 2.4.
         */
        private record FopAvalonConfigurationAdapter(
            Configuration avalonConfiguration) implements org.apache.fop.configuration.Configuration {
            private FopAvalonConfigurationAdapter(final Configuration avalonConfiguration) {
                this.avalonConfiguration = Objects.requireNonNull(avalonConfiguration, "Avalon Configuration must not be null");
            }

            @Override
            public org.apache.fop.configuration.Configuration getChild(final String child) {
                return new FopAvalonConfigurationAdapter(avalonConfiguration.getChild(child));
            }

            @Override
            public @Nullable
            org.apache.fop.configuration.Configuration getChild(final String child, boolean createNew) {
                final Configuration childConfig = avalonConfiguration.getChild(child, createNew);
                if (childConfig == null) {
                    return null;
                }
                return new FopAvalonConfigurationAdapter(childConfig);
            }

            @Override
            public @Nullable
            org.apache.fop.configuration.Configuration[] getChildren(final String name) {
                final Configuration[] children = avalonConfiguration.getChildren(name);
                if (children == null) {
                    return null;
                }

                final org.apache.fop.configuration.Configuration[] newChildren = new org.apache.fop.configuration.Configuration[children.length];
                for (int i = 0; i < children.length; i++) {
                    newChildren[i] = new FopAvalonConfigurationAdapter(children[i]);
                }
                return newChildren;
            }

            @Override
            public String[] getAttributeNames() {
                return avalonConfiguration.getAttributeNames();
            }

            @Override
            public String getAttribute(final String paramName) throws ConfigurationException {
                try {
                    return avalonConfiguration.getAttribute(paramName);
                } catch (final org.apache.avalon.framework.configuration.ConfigurationException e) {
                    throw new ConfigurationException(e.getMessage(), e);
                }
            }

            @Override
            public String getAttribute(final String name, final String defaultValue) {
                return avalonConfiguration.getAttribute(name, defaultValue);
            }

            @Override
            public boolean getAttributeAsBoolean(final String name, final boolean defaultValue) {
                return avalonConfiguration.getAttributeAsBoolean(name, defaultValue);
            }

            @Override
            public float getAttributeAsFloat(final String paramName) throws ConfigurationException {
                try {
                    return avalonConfiguration.getAttributeAsFloat(paramName);
                } catch (final org.apache.avalon.framework.configuration.ConfigurationException e) {
                    throw new ConfigurationException(e.getMessage(), e);
                }
            }

            @Override
            public float getAttributeAsFloat(final String name, final float defaultValue) {
                return avalonConfiguration.getAttributeAsFloat(name, defaultValue);
            }

            @Override
            public int getAttributeAsInteger(final String name, final int defaultValue) {
                return avalonConfiguration.getAttributeAsInteger(name, defaultValue);
            }

            @Override
            public String getValue() throws ConfigurationException {
                try {
                    return avalonConfiguration.getValue();
                } catch (final org.apache.avalon.framework.configuration.ConfigurationException e) {
                    throw new ConfigurationException(e.getMessage(), e);
                }
            }

            @Override
            public String getValue(final String defaultValue) {
                return avalonConfiguration.getValue(defaultValue);
            }

            @Override
            public boolean getValueAsBoolean() throws ConfigurationException {
                try {
                    return avalonConfiguration.getValueAsBoolean();
                } catch (final org.apache.avalon.framework.configuration.ConfigurationException e) {
                    throw new ConfigurationException(e.getMessage(), e);
                }
            }

            @Override
            public boolean getValueAsBoolean(final boolean defaultValue) {
                return avalonConfiguration.getValueAsBoolean(defaultValue);
            }

            @Override
            public int getValueAsInteger() throws ConfigurationException {
                try {
                    return avalonConfiguration.getValueAsInteger();
                } catch (final org.apache.avalon.framework.configuration.ConfigurationException e) {
                    throw new ConfigurationException(e.getMessage(), e);
                }
            }

            @Override
            public int getValueAsInteger(final int defaultValue) {
                return avalonConfiguration.getValueAsInteger(defaultValue);
            }

            @Override
            public float getValueAsFloat() throws ConfigurationException {
                try {
                    return avalonConfiguration.getValueAsFloat();
                } catch (final org.apache.avalon.framework.configuration.ConfigurationException e) {
                    throw new ConfigurationException(e.getMessage(), e);
                }
            }

            @Override
            public float getValueAsFloat(final float defaultValue) {
                return avalonConfiguration.getValueAsFloat(defaultValue);
            }

            @Override
            public String getLocation() {
                return avalonConfiguration.getLocation();
            }
        }

    private static class ExistLoggingEventListener implements EventListener {
        private final Logger log;
        private final Set<String> loggedMessages = new HashSet<>();

        public ExistLoggingEventListener(final Logger log) {
            this.log = log;
        }

        @Override
        public void processEvent(final Event event) {
            final String msg = EventFormatter.format(event);
            final EventSeverity severity = event.getSeverity();
            if (severity == EventSeverity.INFO) {
                log.info(msg);
            } else if (severity == EventSeverity.WARN) {
                // we want to prevent logging of duplicate messages in situations where they are likely
                // to occur; for instance, warning related to layout do not repeat (since line number
                // will be different) and as such we do not try to filter them here; on the other hand,
                // font related warnings are very likely to repeat and we try to filter them out here;
                // the same may happen with missing images (but not implemented yet).
                final String eventGroupID = event.getEventGroupID();
                if ("org.apache.fop.fonts.FontEventProducer".equals(eventGroupID)) {
                    if (!loggedMessages.contains(msg)) {
                        loggedMessages.add(msg);
                        log.warn(msg);
                    }
                } else {
                    log.warn(msg);
                }
            } else if (severity == EventSeverity.ERROR) {
                if (event.getParam("e") != null) {
                    log.error(msg, (Throwable) event.getParam("e"));
                } else {
                    log.error(msg);
                }
            } else if (severity == EventSeverity.FATAL) {

                if (event.getParam("e") != null) {
                    log.fatal(msg, (Throwable) event.getParam("e"));
                } else {
                    log.fatal(msg);
                }
            } else {
                assert false;
            }
        }
    }
}
