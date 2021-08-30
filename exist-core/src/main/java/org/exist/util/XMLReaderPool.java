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

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.StackObjectPool;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.xml.sax.XMLReader;

/**
 * Maintains a pool of XMLReader objects.
 *
 * The pool is available through {@link BrokerPool#getParserPool()}.
 */
public class XMLReaderPool extends StackObjectPool<XMLReader> implements BrokerPoolService {

    /**
     * Constructs an XML Reader Pool.
     *
     * @param factory          the object factory
     * @param maxIdle          the max idle time for a reader
     * @param initIdleCapacity the initial capacity
     */
    public XMLReaderPool(final PoolableObjectFactory<XMLReader> factory, final int maxIdle, final int initIdleCapacity) {
        super(factory, maxIdle, initIdleCapacity);
    }

    @Override
    public void configure(final Configuration configuration) {
        //nothing to configure
    }

    public XMLReader borrowXMLReader() {
        try {
            return super.borrowObject();
        } catch (final Exception e) {
            throw new IllegalStateException("error while returning XMLReader: " + e.getMessage(), e);
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
            throw new IllegalStateException("error while returning XMLReader: " + e.getMessage(), e);
        }
    }

    @Override
    public void returnObject(final XMLReader obj) throws Exception {
        returnXMLReader(obj);
    }

    // just used for config properties
    public interface XmlParser {
        String XML_PARSER_ELEMENT = "xml";
        String XML_PARSER_FEATURES_ELEMENT = "features";
        String XML_PARSER_FEATURES_PROPERTY = "parser.xml-parser.features";
    }
}
