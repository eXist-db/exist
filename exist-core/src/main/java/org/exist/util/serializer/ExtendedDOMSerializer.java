/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.util.serializer;

import java.io.Writer;
import java.util.Properties;

import javax.xml.transform.TransformerException;

import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.ReferenceNode;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;


/**
 * @author wolf
 */
public class ExtendedDOMSerializer extends DOMSerializer {

    private DBBroker broker;

    /**
     * @param broker the database broker
     * @param writer the destination
     * @param outputProperties the output proprerties
     */
    public ExtendedDOMSerializer(DBBroker broker, Writer writer, Properties outputProperties) {
        super(writer, outputProperties);
        this.broker = broker;
    }

    @Override
    protected void startNode(Node node) throws TransformerException {
        if(node.getNodeType() == NodeImpl.REFERENCE_NODE) {
            final SAXSerializer handler = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            handler.setReceiver(receiver);
            final Serializer serializer = broker.getSerializer();
            serializer.setSAXHandlers(handler, handler);
            try {
                serializer.setProperties(outputProperties);
                serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
            } catch (final SAXNotRecognizedException e) {
                //Nothing to do ?
            } catch (final SAXNotSupportedException e) {
                //Nothing to do ?
            }
            try {
                serializer.toSAX(((ReferenceNode)node).getReference());
            } catch (final SAXException e) {
                throw new TransformerException(e.getMessage(), e);
            } finally {
                SerializerPool.getInstance().returnObject(handler);
            }
        } else
            {super.startNode(node);}
    }
}
