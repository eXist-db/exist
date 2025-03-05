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
package org.exist.xslt;

import java.util.Hashtable;
import java.util.Iterator;

import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXTransformerFactory;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;

/**
 * Allows the TransformerFactory that is used for XSLT to be
 * chosen through configuration settings in conf.xml
 *
 * Within eXist this class should be used instead of
 * directly calling SAXTransformerFactory.newInstance() directly
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 */

public class TransformerFactoryAllocator {
    private final static Logger LOG = LogManager.getLogger(TransformerFactoryAllocator.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "transformer";
    public final static String TRANSFORMER_CLASS_ATTRIBUTE = "class";
    public final static String PROPERTY_TRANSFORMER_CLASS = "transformer.class";

    public final static String CONFIGURATION_TRANSFORMER_ATTRIBUTE_ELEMENT_NAME = "attribute";
    public final static String PROPERTY_TRANSFORMER_ATTRIBUTES = "transformer.attributes";

    public final static String TRANSFORMER_CACHING_ATTRIBUTE = "caching";
    public final static String PROPERTY_CACHING_ATTRIBUTE = "transformer.caching";

    public final static String PROPERTY_BROKER_POOL = "transformer.brokerPool";

    //private constructor
    private TransformerFactoryAllocator() {
    }

    /**
     * Get the TransformerFactory defined in conf.xml
     * If the class can't be found or the given class doesn't implement
     * the required interface, the default factory is returned.
     *
     * @param pool A database broker pool, used for reading the conf.xml configuration
     * @return A SAXTransformerFactory, for which newInstance() can then be called
     *
     * Typical usage:
     *
     * Instead of SAXTransformerFactory.newInstance() use
     * TransformerFactoryAllocator.getTransformerFactory(broker).newInstance()
     */
    public static SAXTransformerFactory getTransformerFactory(final BrokerPool pool) {
        //Get the transformer class name from conf.xml
        final String transformerFactoryClassName = (String) pool.getConfiguration().getProperty(PROPERTY_TRANSFORMER_CLASS);

        //Was a TransformerFactory class specified?
        SAXTransformerFactory factory;
        if (transformerFactoryClassName == null) {
            //No, use the system default
            factory = (SAXTransformerFactory) TransformerFactory.newInstance();
        } else {
            //Try and load the specified TransformerFactory class
            try {
                factory = (SAXTransformerFactory) Class.forName(transformerFactoryClassName).newInstance();
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Set transformer factory: {}", transformerFactoryClassName);
                }
                final Hashtable<String, Object> attributes = (Hashtable<String, Object>) pool.getConfiguration().getProperty(PROPERTY_TRANSFORMER_ATTRIBUTES);
                for (String name : attributes.keySet()) {
                    final Object value = attributes.get(name);
                    try {
                        factory.setAttribute(name, value);
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Set transformer attribute: , name: {}, value: {}", name, value);
                        }
                    } catch (final IllegalArgumentException iae) {
                        LOG.warn("Unable to set attribute for TransformerFactory: '{}', name: {}, value: {}, exception: {}", transformerFactoryClassName, name, value, iae.getMessage());
                    }
                }
                try {
                    factory.setAttribute(PROPERTY_BROKER_POOL, pool);
                } catch (final Exception e) {
                    //some transformers do not support "setAttribute"
                }
            } catch (final ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Error found loading the requested TrAX Transformer Factory '{}'. Using default TrAX Transformer Factory instead: {}", transformerFactoryClassName, e);
                }
                //Fallback to system default
                factory = (SAXTransformerFactory) TransformerFactory.newInstance();
            }
        }

        return factory;
    }

}
