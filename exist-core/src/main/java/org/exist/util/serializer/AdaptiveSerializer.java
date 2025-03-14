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

package org.exist.util.serializer;

import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.Writer;
import java.util.Objects;
import java.util.Properties;

/**
 * Implements adaptive serialization from the <a href="https://www.w3.org/TR/xslt-xquery-serialization-31/">XSLT and
 * XQuery Serialization 3.1</a> specification.
 *
 * @author Wolfgang
 */
public class AdaptiveSerializer extends AbstractSerializer {

    private final static String DEFAULT_ITEM_SEPARATOR = "\n";

    private final DBBroker broker;
    private AdaptiveWriter adaptiveWriter;

    public AdaptiveSerializer(final DBBroker broker) {
        super();
        this.broker = broker;
    }

    @Override
    public void setOutput(final Writer writer, final Properties properties) {
        outputProperties = Objects.requireNonNullElseGet(properties, () -> new Properties(defaultProperties));
        for (SerializerWriter w: writers) {
            w.setWriter(writer);
            w.setOutputProperties(outputProperties);
        }
        adaptiveWriter = new AdaptiveWriter(broker, outputProperties, writers[TEXT_WRITER]);
    }

    public void serialize(final Sequence sequence) throws XPathException, SAXException {
        final String itemSep = outputProperties.getProperty(EXistOutputKeys.ITEM_SEPARATOR, DEFAULT_ITEM_SEPARATOR);
        try {
            adaptiveWriter.write(sequence, itemSep, false);
        } catch (TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }
}
