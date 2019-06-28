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
import org.apache.ws.commons.util.NamespaceContextImpl;
import org.apache.xmlrpc.common.TypeFactoryImpl;
import org.apache.xmlrpc.common.XmlRpcController;
import org.apache.xmlrpc.common.XmlRpcStreamConfig;
import org.apache.xmlrpc.parser.TypeParser;
import org.apache.xmlrpc.serializer.TypeSerializer;
import org.xml.sax.SAXException;

/**
 * Custom XML-RPC type factory to enable the use
 * of extended types in XML-RPC with eXist-db.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ExistRpcTypeFactory extends TypeFactoryImpl {
    public ExistRpcTypeFactory(final XmlRpcController controller) {
        super(controller);
    }

    @Override
    public TypeParser getParser(final XmlRpcStreamConfig config, final NamespaceContextImpl context, final String uri, final String localName) {
        if (TupleSerializer.TUPLE_TAG.equals(localName)) {
            return new TupleParser(config, context, this);
        } else {
            return super.getParser(config, context, uri, localName);
        }
    }

    @Override
    public TypeSerializer getSerializer(final XmlRpcStreamConfig config, final Object object) throws SAXException {
        if (object instanceof Tuple) {
            return new TupleSerializer(this, config);
        } else {
            return super.getSerializer(config, object);
        }
    }
}
