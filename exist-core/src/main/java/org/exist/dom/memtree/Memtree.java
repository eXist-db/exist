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
package org.exist.dom.memtree;

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.Receiver;
import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;

/**
 * An in-memory representation of a Node Tree for DOM purposes.
 */
public interface Memtree {

    void reset();

    int getSize();

    short getTreeLevel(final int nodeNumber);

    short getNodeType(final int nodeNumber);

    QName getNodeName(final int nodeNumber);

    NodeId getNodeId(final int nodeNumber);

    int getDocumentElement();

    int getAttributesCountFor(final int nodeNumber);

    int getNamespacesCountFor(final int nodeNumber);

    int getChildCountFor(final int nodeNumber);

    int getFirstChildFor(final int nodeNumber);

    int getLastChildFor(final int nodeNumber);

    int getNextSiblingFor(final int nodeNumber);

    int getParentNodeFor(final int nodeNumber);

    int getLastNode();

    int getIdAttribute(final int nodeNumber, final String id);

    int getIdrefAttribute(final int nodeNumber, final String id);

    int getLastAttr();

    int addNode(final short kind, final short level, final QName qname);

    int addNamespace(final int nodeNumber, final QName qname);

    int addAttribute(final int nodeNumber, final QName qname, final String value, final int type) throws DOMException;

    void addChars(final int nodeNumber, final char[] ch, final int start, final int len);

    void addChars(final int nodeNumber, final CharSequence s);

    void appendChars(final int nodeNumber, final char[] ch, final int start, final int len);

    void appendChars(final int nodeNumber, final CharSequence s);

    void addReferenceNode(final int nodeNumber, final NodeProxy proxy);

    boolean hasReferenceNodes();

    void replaceReferenceNode(final int nodeNumber, final CharSequence ch);


    // TODO(AR) should these functions below this point be in this interface or moved elsewhere?

    void computeNodeIds(final NodeIdFactory nodeIdFactory);

    void copyTo(int nodeNumber, final DocumentBuilderReceiver receiver, @Nullable final ConsumerE<NodeProxy, SAXException> referenceNodeReceiver) throws SAXException;

    /**
     * Stream the specified document fragment to a receiver. This method
     * is called by the serializer to output in-memory nodes.
     *
     * @param serializer the serializer
     * @param nodeNumber       node to be serialized
     * @param receiver   the receiver
     * @throws SAXException if an error occurs
     */
    void streamTo(final Serializer serializer, final int nodeNumber, final Receiver receiver) throws SAXException;
}
