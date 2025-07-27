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
package org.exist.xquery.modules.exi;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Type;

public class EXIUtils {
	
	private static final Logger LOG = LogManager.getLogger(EXIUtils.class);
	
	protected static InputStream getInputStream(Item item, XQueryContext context, final Expression expression) throws XPathException, MalformedURLException, IOException {
        switch (item.getType()) {
            case Type.ANY_URI:
                LOG.debug("Streaming xs:anyURI");

                // anyURI provided
                String url = item.getStringValue();

                // Fix URL
                if (url.startsWith("/")) {
                    url = "xmldb:exist://" + url;
                }

                return URI.create(url).toURL().openStream();
            case Type.ELEMENT:
            case Type.DOCUMENT:
                LOG.debug("Streaming element or document node");

            /*
            if (item instanceof NodeProxy) {
                NodeProxy np = (NodeProxy) item;
                String url = "xmldb:exist://" + np.getDocument().getBaseURI();
                LOG.debug("Document detected, adding URL " + url);
                streamSource.setSystemId(url);
            }
            */

                // Node provided
                final ConsumerE<ConsumerE<Serializer, IOException>, IOException> withSerializerFn = fn -> {
                    final Serializer serializer = context.getBroker().borrowSerializer();
                    try {
                        fn.accept(serializer);
                    } finally {
                        context.getBroker().returnSerializer(serializer);
                    }
                };

                NodeValue node = (NodeValue) item;
                return new NodeInputStream(context.getBroker().getBrokerPool(), withSerializerFn, node);
            default:
                LOG.error("Wrong item type {}", Type.getTypeName(item.getType()));
                throw new XPathException(expression, "wrong item type " + Type.getTypeName(item.getType()));
        }
    }

}