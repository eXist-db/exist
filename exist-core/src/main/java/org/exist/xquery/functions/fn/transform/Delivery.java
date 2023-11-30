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

package org.exist.xquery.functions.fn.transform;

import net.sf.saxon.s9api.*;
import net.sf.saxon.serialize.SerializationProperties;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;

import java.io.StringWriter;
import java.util.Objects;

class Delivery {

    enum Format {
        DOCUMENT,
        SERIALIZED,
        RAW
    }

    final XQueryContext context;
    final Format format;
    final SerializationProperties serializationProperties;
    MemTreeBuilder builder;
    StringWriter stringWriter;

    RawDestination rawDestination;

    Delivery(final XQueryContext context, final Format format, final SerializationProperties serializationProperties) {
        this.context = context;
        this.format = format;
        this.serializationProperties = serializationProperties;
    }

    final Destination createDestination(final Xslt30Transformer xslt30Transformer, final boolean forceCreation) {
        switch (format) {
            case DOCUMENT:
                if (!forceCreation) {
                    this.builder = context.getDocumentBuilder();
                } else {
                    this.builder = new MemTreeBuilder(context);
                    this.builder.startDocument();
                }
                return new SAXDestination(new DocumentBuilderReceiver(builder));
            case SERIALIZED:
                final Serializer serializer = xslt30Transformer.newSerializer();
                final SerializationProperties stylesheetProperties = serializer.getSerializationProperties();

                final SerializationProperties combinedProperties =
                        SerializationParameters.combinePropertiesAndCharacterMaps(
                                stylesheetProperties,
                                serializationProperties);

                serializer.setOutputProperties(combinedProperties);
                stringWriter = new StringWriter();
                serializer.setOutputWriter(stringWriter);
                return serializer;
            case RAW:
                this.rawDestination = new RawDestination();
                return rawDestination;
            default:
                return null;
        }
    }

    private String getSerializedString() {

        if (stringWriter == null) {
            return null;
        }
        return stringWriter.getBuffer().toString();
    }

    private DocumentImpl getDocument() {
        if (builder == null) {
            return null;
        }
        return builder.getDocument();
    }

    private XdmValue getXdmValue() {
        if (rawDestination == null) {
            return null;
        }
        return rawDestination.getXdmValue();
    }

    Sequence convert() throws XPathException {

        return switch (format) {
            case SERIALIZED -> new StringValue(getSerializedString());
            case RAW -> Convert.ToExist.of(Objects.requireNonNull(getXdmValue()));
            default -> getDocument();
        };
    }
}
