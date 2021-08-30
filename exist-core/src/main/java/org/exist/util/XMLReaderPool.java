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
package org.exist.util;

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.xml.sax.XMLReader;

/**
 * Maintains a pool of XMLReader objects.
 *
 * The pool is available through {@link BrokerPool#getParserPool()}.
 */
@ThreadSafe
public class XMLReaderPool extends GenericObjectPool<XMLReader> implements BrokerPoolService {

    /**
     * Constructs an XML Reader Pool.
     *
     * @param factory the XMLReader object factory
     * @param maxIdle the maximum number of idle instances in the pool
     * @param initSize initial size of the pool (this specifies the size of the container, it does not cause the pool to be pre-populated.) (unused)
     */
    public XMLReaderPool(final PooledObjectFactory<XMLReader> factory, final int maxIdle, @Deprecated final int initSize) {
        super(factory, toConfig(maxIdle));
    }

    private static GenericObjectPoolConfig toConfig(final int maxIdle) {
        final GenericObjectPoolConfig<Object> config = new GenericObjectPoolConfig<>();
        config.setLifo(true);
        config.setMaxIdle(maxIdle);
        return config;
    }

    @Override
    public void configure(final Configuration configuration) {
        //nothing to configure
    }

    public XMLReader borrowXMLReader() {
        try {
            return super.borrowObject();
        } catch (final Exception e) {
            throw new IllegalStateException("Error while borrowing: " + e.getMessage(), e);
        }
    }

    @Override
    public XMLReader borrowObject() throws Exception {
        return borrowXMLReader();
    }

    public void returnXMLReader(final XMLReader reader) {
        try {
            super.returnObject(reader);

        } catch (final Exception e) {
            throw new IllegalStateException("Error while returning: " + e.getMessage(), e);
        }
    }

    @Override
    public void returnObject(final XMLReader obj) {
        returnXMLReader(obj);
    }

    // just used for config properties
    public interface XmlParser {
        String XML_PARSER_ELEMENT = "xml";
        String XML_PARSER_FEATURES_ELEMENT = "features";
        String XML_PARSER_FEATURES_PROPERTY = "parser.xml-parser.features";
    }
}
