/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xmlrpc;

import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.apache.xmlrpc.serializer.TypeSerializerImpl;
import org.exist.security.internal.aider.ACEAider;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * XML-RPC type serializer for objects of
 * {@link org.exist.security.internal.aider.ACEAider}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class ACEAiderSerializer extends TypeSerializerImpl {
    static final String ACEAIDER_TAG = "aceAider";
    static final String DATA_TAG = "data";

    private final TypeFactory typeFactory;
    private final XmlRpcStreamConfig config;

    ACEAiderSerializer(final TypeFactory typeFactory, final XmlRpcStreamConfig config) {
        this.typeFactory = typeFactory;
        this.config = config;
    }

    private void writeObject(final ContentHandler handler, final Object object) throws SAXException {
        final TypeSerializer ts = typeFactory.getSerializer(config, object);
        if (ts == null) {
            throw new SAXException("Unsupported Java type: " + object.getClass().getName());
        }
        ts.write(handler, object);
    }

    private void writeData(final ContentHandler handler, final Object object) throws SAXException {
        final ACEAider aceAider = (ACEAider) object;
        writeObject(handler, aceAider.getAccessType().name());
        writeObject(handler, aceAider.getTarget().name());
        writeObject(handler, aceAider.getWho());
        writeObject(handler, aceAider.getMode());
    }

    @Override
    public void write(final ContentHandler pHandler, final Object pObject) throws SAXException {
        pHandler.startElement("", VALUE_TAG, VALUE_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", ACEAIDER_TAG, ACEAIDER_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", DATA_TAG, DATA_TAG, ZERO_ATTRIBUTES);
        writeData(pHandler, pObject);
        pHandler.endElement("", DATA_TAG, DATA_TAG);
        pHandler.endElement("", ACEAIDER_TAG, ACEAIDER_TAG);
        pHandler.endElement("", VALUE_TAG, VALUE_TAG);
    }
}
