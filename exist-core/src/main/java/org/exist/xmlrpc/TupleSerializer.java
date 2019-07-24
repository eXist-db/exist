/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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
        if(tuple instanceof Tuple2) {
            writeObject(handler, ((Tuple2)tuple)._1);
            writeObject(handler, ((Tuple2)tuple)._2);
        } else if(tuple instanceof Tuple3) {
            writeObject(handler, ((Tuple3)tuple)._1);
            writeObject(handler, ((Tuple3)tuple)._2);
            writeObject(handler, ((Tuple3)tuple)._3);
        } else if(tuple instanceof Tuple4) {
            writeObject(handler, ((Tuple4)tuple)._1);
            writeObject(handler, ((Tuple4)tuple)._2);
            writeObject(handler, ((Tuple4)tuple)._3);
            writeObject(handler, ((Tuple4)tuple)._4);
        } else if(tuple instanceof Tuple5) {
            writeObject(handler, ((Tuple5)tuple)._1);
            writeObject(handler, ((Tuple5)tuple)._2);
            writeObject(handler, ((Tuple5)tuple)._3);
            writeObject(handler, ((Tuple5)tuple)._4);
            writeObject(handler, ((Tuple5)tuple)._5);
        } else if(tuple instanceof Tuple6) {
            writeObject(handler, ((Tuple6) tuple)._1);
            writeObject(handler, ((Tuple6) tuple)._2);
            writeObject(handler, ((Tuple6) tuple)._3);
            writeObject(handler, ((Tuple6) tuple)._4);
            writeObject(handler, ((Tuple6) tuple)._5);
            writeObject(handler, ((Tuple6) tuple)._6);
        } else {
            throw new SAXException("Unsupported Tuple class: " + tuple.getClass().getName());
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
