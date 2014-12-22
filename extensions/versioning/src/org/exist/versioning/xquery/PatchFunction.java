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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.InMemoryXMLStreamReader;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.versioning.DiffException;
import org.exist.versioning.Patch;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

public class PatchFunction extends BasicFunction {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName( "patch", VersioningModule.NAMESPACE_URI, VersioningModule.PREFIX ),
                    "Apply a patch to a document. The patch will be applied to the document of the node " +
                    "passed in first parameter. The second parameter should contain a version document as generated " +
                    "by eXist's VersioningTrigger. Note: though an arbitrary node can be passed in $a, the patch will " +
                    "always be applied to the entire document to which this node belongs.",
                    new SequenceType[] {
                            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
                            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                    },
                    new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
            ),
            new FunctionSignature(
                    new QName( "annotate", VersioningModule.NAMESPACE_URI, VersioningModule.PREFIX ),
                    "Apply a patch to a document. The patch will be applied to the document of the node " +
                            "passed in first parameter. The second parameter should contain a version document as generated " +
                            "by eXist's VersioningTrigger. Note: though an arbitrary node can be passed in $a, the patch will " +
                            "always be applied to the entire document to which this node belongs.",
                    new SequenceType[] {
                            new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
                            new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE)
                    },
                    new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
            )
    };

    public PatchFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        context.pushDocumentContext();
        try {
            ExtendedXMLStreamReader reader;
            NodeValue nv = (NodeValue) args[0].itemAt(0);
            if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
                NodeImpl node = (NodeImpl) nv;
                reader = new InMemoryXMLStreamReader(node.getOwnerDocument(), node.getOwnerDocument());
            } else {
                NodeProxy proxy = (NodeProxy) nv;
                reader = context.getBroker().newXMLStreamReader(new NodeProxy(proxy.getOwnerDocument(), NodeId.DOCUMENT_NODE, proxy.getOwnerDocument().getFirstChildAddress()), false);
            }

            nv = (NodeValue) args[1].itemAt(0);
            if (nv.getImplementationType() == NodeValue.IN_MEMORY_NODE)
                throw new XPathException("patch cannot be applied to in-memory documents");
            NodeProxy diffProxy = (NodeProxy) nv;
            DocumentImpl diff = diffProxy.getOwnerDocument();
        
            MemTreeBuilder builder = context.getDocumentBuilder();
            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
            Patch patch = new Patch(context.getBroker(), diff);
            if (isCalledAs("annotate"))
                patch.annotate(reader, receiver);
            else
                patch.patch(reader, receiver);
            NodeValue result = (NodeValue) builder.getDocument().getDocumentElement();
            return result == null ? Sequence.EMPTY_SEQUENCE : result;
        } catch (IOException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (XMLStreamException e) {
            throw new XPathException(this, e.getMessage(), e);
        } catch (DiffException e) {
            throw new XPathException(this, e.getMessage(), e);
        } finally {
            context.popDocumentContext();
        }
    }
}
