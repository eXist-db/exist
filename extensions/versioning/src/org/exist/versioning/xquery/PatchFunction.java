/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.versioning.xquery;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.functions.system.SystemModule;
import org.exist.dom.QName;
import org.exist.dom.NodeProxy;
import org.exist.dom.ElementImpl;
import org.exist.dom.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.InMemoryXMLStreamReader;
import org.exist.memtree.NodeImpl;
import org.exist.versioning.Patch;
import org.exist.versioning.DiffException;
import org.exist.stax.ExtendedXMLStreamReader;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class PatchFunction extends BasicFunction {

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName( "patch", VersioningModule.NAMESPACE_URI, VersioningModule.PREFIX ),
                    "Apply a patch to a document.",
                    new SequenceType[] {
                            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
                            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                    },
                    new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
            );

    public PatchFunction(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        context.pushDocumentContext();
        try {
            ExtendedXMLStreamReader reader;
            NodeValue nv = (NodeValue) args[0].itemAt(0);
            if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                NodeImpl node = (NodeImpl) nv;
                reader = new InMemoryXMLStreamReader(node.getDocument(), node);
            } else {
                NodeProxy proxy = (NodeProxy) nv;
                ElementImpl root = (ElementImpl) proxy.getDocument().getDocumentElement();
                reader = context.getBroker().newXMLStreamReader(root, false);
            }

            nv = (NodeValue) args[1].itemAt(0);
            if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                throw new XPathException("patch cannot be applied to in-memory documents");
            NodeProxy diffProxy = (NodeProxy) nv;
            DocumentImpl diff = diffProxy.getDocument();
        
            MemTreeBuilder builder = context.getDocumentBuilder();
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            Patch patch = new Patch(context.getBroker(), diff);
            patch.patch(reader, receiver);
            return (NodeValue) builder.getDocument().getDocumentElement();
        } catch (IOException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
        } catch (XMLStreamException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
        } catch (DiffException e) {
            throw new XPathException(getASTNode(), e.getMessage(), e);
        } finally {
            context.popDocumentContext();
        }
    }
}
