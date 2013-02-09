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

import org.exist.memtree.NodeImpl;
import org.exist.memtree.ReferenceNode;
import org.exist.storage.serializers.Serializer;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;


/**
 * @author wolf
 */
public class ExtendedDOMStreamer extends DOMStreamer {

    private Serializer xmlSerializer;
    
    public ExtendedDOMStreamer() {
        super();
    }

    /**
     * 
     */
    public ExtendedDOMStreamer(Serializer xmlSerializer) {
        super();
        this.xmlSerializer = xmlSerializer;
    }

    /**
     * @param contentHandler
     * @param lexicalHandler
     */
    public ExtendedDOMStreamer(Serializer xmlSerializer, ContentHandler contentHandler,
            LexicalHandler lexicalHandler) {
        super(contentHandler, lexicalHandler);
        this.xmlSerializer = xmlSerializer;
    }

    public void setSerializer(Serializer serializer) {
        this.xmlSerializer = serializer;
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.DOMStreamer#startNode(org.w3c.dom.Node)
     */
    @Override
    protected void startNode(Node node) throws SAXException {
        if(node.getNodeType() == NodeImpl.REFERENCE_NODE) {
            if(xmlSerializer == null)
                {throw new SAXException("Cannot serialize node reference. Serializer is undefined.");}
            xmlSerializer.toReceiver(((ReferenceNode)node).getReference(), true);
        } else
            {super.startNode(node);}
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.DOMStreamer#reset()
     */
    @Override
    public void reset() {
        super.reset();
        xmlSerializer = null;
    }
}
