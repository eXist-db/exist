/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.dom.memtree;

import org.exist.dom.QName;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;


public class ProcessingInstructionImpl extends NodeImpl implements ProcessingInstruction {

    public ProcessingInstructionImpl(final DocumentImpl doc, final int nodeNumber) {
        super(doc, nodeNumber);
    }

    @Override
    public String getTarget() {
        final QName qname = getQName();
        return qname != null ? qname.getLocalPart() : null;
    }

    @Override
    public String getStringValue() {
        return getData().replaceFirst("^\\s+", "");
    }

    @Override
    public String getData() {
        return new String(document.characters, document.alpha[nodeNumber], document.alphaLen[nodeNumber]);
    }

    @Override
    public String getNodeValue() throws DOMException {
        return getData();
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        return new StringValue(getData());
    }

    @Override
    public void setData(final String data) throws DOMException {
        document.characters = data.toCharArray();
        document.alpha[nodeNumber] = 0;
        document.alphaLen[nodeNumber] = data.length();
    }

    @Override
    public String getBaseURI() {
        String baseURI = "";
        int parent = -1;
        int test = document.getParentNodeFor(nodeNumber);

        if(document.nodeKind[test] != Node.DOCUMENT_NODE) {
            parent = test;
        }

        // fixme! Testa med 0/ljo
        while((parent != -1) && (document.getNode(parent).getBaseURI() != null)) {

            if(baseURI.isEmpty()) {
                baseURI = document.getNode(parent).getBaseURI();
            } else {
                baseURI = document.getNode(parent).getBaseURI() + "/" + baseURI;
            }

            test = document.getParentNodeFor(parent);

            if(document.nodeKind[test] == Node.DOCUMENT_NODE) {
                return (baseURI);
            } else {
                parent = test;
            }
        }

        if(baseURI.isEmpty()) {
            baseURI = getOwnerDocument().getBaseURI();
        }
        return (baseURI);
    }

    @Override
    public Node getFirstChild() {
        //No child
        return null;
    }

    @Override
    public int getItemType() {
        return Type.PROCESSING_INSTRUCTION;
    }

    @Override
    public String toString() {
        return "in-memory#processing-instruction {" + getTarget() + "} {" + getData() + "} ";
    }

    @Override
    public void selectAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    @Override
    public void selectChildren(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }

    @Override
    public void selectDescendantAttributes(final NodeTest test, final Sequence result)
            throws XPathException {
        //do nothing, which will return an empty sequence
    }
}
