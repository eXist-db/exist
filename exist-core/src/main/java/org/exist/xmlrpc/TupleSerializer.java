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
package org.exist.xmlrpc;

import com.evolvedbinary.j8fu.tuple.*;
import org.apache.xmlrpc.common.TypeFactory;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.apache.xmlrpc.serializer.TypeSerializerImpl;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * XML-RPC type serializer for sub-classes of
 * {@link com.evolvedbinary.j8fu.tuple.Tuple}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
class TupleSerializer extends TypeSerializerImpl {
    public static final String TUPLE_TAG = "tuple";
    public static final String DATA_TAG = "data";

    private final TypeFactory typeFactory;
    private final XmlRpcStreamConfig config;

    TupleSerializer(final TypeFactory typeFactory, final XmlRpcStreamConfig config) {
        this.typeFactory = typeFactory;
        this.config = config;
    }

    private void writeObject(final ContentHandler handler, final Object object) throws SAXException {
        TypeSerializer ts = typeFactory.getSerializer(config, object);
        if (ts == null) {
            throw new SAXException("Unsupported Java type: " + object.getClass().getName());
        }
        ts.write(handler, object);
    }

    private void writeData(final ContentHandler handler, final Object object) throws SAXException {
        final Tuple tuple = (Tuple) object;
        switch (tuple) {
            case Tuple2 tuple2 -> {
                writeObject(handler, tuple2._1);
                writeObject(handler, tuple2._2);
            }
            case Tuple3 tuple3 -> {
                writeObject(handler, tuple3._1);
                writeObject(handler, tuple3._2);
                writeObject(handler, tuple3._3);
            }
            case Tuple4 tuple4 -> {
                writeObject(handler, tuple4._1);
                writeObject(handler, tuple4._2);
                writeObject(handler, tuple4._3);
                writeObject(handler, tuple4._4);
            }
            case Tuple5 tuple5 -> {
                writeObject(handler, tuple5._1);
                writeObject(handler, tuple5._2);
                writeObject(handler, tuple5._3);
                writeObject(handler, tuple5._4);
                writeObject(handler, tuple5._5);
            }
            case Tuple6 tuple6 -> {
                writeObject(handler, tuple6._1);
                writeObject(handler, tuple6._2);
                writeObject(handler, tuple6._3);
                writeObject(handler, tuple6._4);
                writeObject(handler, tuple6._5);
                writeObject(handler, tuple6._6);
            }
            case null, default -> throw new SAXException("Unsupported Tuple class: " + tuple.getClass().getName());
        }
    }

    @Override
    public void write(final ContentHandler pHandler, Object pObject) throws SAXException {
        pHandler.startElement("", VALUE_TAG, VALUE_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", TUPLE_TAG, TUPLE_TAG, ZERO_ATTRIBUTES);
        pHandler.startElement("", DATA_TAG, DATA_TAG, ZERO_ATTRIBUTES);
        writeData(pHandler, pObject);
        pHandler.endElement("", DATA_TAG, DATA_TAG);
        pHandler.endElement("", TUPLE_TAG, TUPLE_TAG);
        pHandler.endElement("", VALUE_TAG, VALUE_TAG);
    }
}
