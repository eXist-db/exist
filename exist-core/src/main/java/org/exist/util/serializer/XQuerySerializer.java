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
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.json.JSONSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

/**
 * Utility class for writing out XQuery results. It is an abstraction around
 * eXist's internal serializers specialized on writing XQuery sequences.
 *
 * @author Wolf
 */
public class XQuerySerializer {

    private final Properties outputProperties;
    private final DBBroker broker;
    private final Writer writer;

    public XQuerySerializer(DBBroker broker, Properties outputProperties, Writer writer) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
        this.writer = writer;

        // ALWAYS enforce XDM serialization rules
        outputProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
    }

    public void serialize(final Sequence sequence) throws SAXException, XPathException {
        serialize(sequence, 1, sequence.getItemCount(), false, false, 0, 0);
    }

    public void serialize(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        switch (method) {
            case "adaptive":
                serializeAdaptive(sequence);
                break;
            case "json":
                serializeJSON(sequence, compilationTime, executionTime);
                break;
            case "xml":
            default:
                serializeXML(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
                break;
        }
    }

    public boolean normalize() {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        return !("json".equals(method) || "adaptive".equals(method));
    }

    private void serializeXML(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final String itemSeparator = outputProperties.getProperty(EXistOutputKeys.ITEM_SEPARATOR);
        // If item-separator is set and sequence has multiple items, serialize items individually
        // with separator between them (the internal Serializer doesn't handle item-separator)
        if (itemSeparator != null && sequence.getItemCount() > 1 && !wrap) {
            serializeXMLWithItemSeparator(sequence, start, howmany, typed, itemSeparator);
        } else {
            serializeXMLDirect(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
        }
    }

    private void serializeXMLDirect(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final Serializer serializer = broker.borrowSerializer();
        SAXSerializer sax = null;
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            sax.setOutput(writer, outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);
            serializer.toSAX(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
        } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
            throw new SAXException(e.getMessage(), e);
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
            broker.returnSerializer(serializer);
        }
    }

    private void serializeXMLWithItemSeparator(final Sequence sequence, final int start, final int howmany, final boolean typed, final String itemSeparator) throws SAXException, XPathException {
        final int actualStart = start - 1; // convert 1-based to 0-based
        final int end = Math.min(actualStart + howmany, sequence.getItemCount());
        for (int i = actualStart; i < end; i++) {
            if (i > actualStart) {
                try {
                    writer.write(itemSeparator);
                } catch (IOException e) {
                    throw new SAXException(e.getMessage(), e);
                }
            }
            final Item item = sequence.itemAt(i);
            if (item == null) {
                continue;
            }
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                final ValueSequence singleItem = new ValueSequence(1);
                singleItem.add(item);
                serializeXMLDirect(singleItem, 1, 1, false, typed, 0, 0);
            } else {
                try {
                    writer.write(item.getStringValue());
                } catch (IOException e) {
                    throw new SAXException(e.getMessage(), e);
                }
            }
        }
    }

    private void serializeJSON(final Sequence sequence, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        // Backwards compatibility: if the sequence contains a single element or document,
        // use the legacy XML-to-JSON writer (which converts XML structure to JSON properties).
        // This is needed for RESTXQ and REST API which return XML documents with method=json.
        // Maps, arrays, atomics, and multi-item sequences go through the W3C-compliant JSONSerializer.
        if (sequence.hasOne() && (Type.subTypeOf(sequence.getItemType(), Type.DOCUMENT) || Type.subTypeOf(sequence.getItemType(), Type.ELEMENT))) {
            serializeXMLDirect(sequence, 1, 1, false, false, compilationTime, executionTime);
        } else {
            JSONSerializer serializer = new JSONSerializer(broker, outputProperties);
            serializer.serialize(sequence, writer);
        }
    }

    private void serializeAdaptive(final Sequence sequence) throws SAXException, XPathException {
        final AdaptiveSerializer serializer = new AdaptiveSerializer(broker);
        serializer.setOutput(writer, outputProperties);
        serializer.serialize(sequence);
    }
}
