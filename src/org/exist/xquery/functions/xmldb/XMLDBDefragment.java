/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
package org.exist.xquery.functions.xmldb;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.update.Modification;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implments the xmldb:defragment() function. 
 *
 *
 */
public class XMLDBDefragment extends BasicFunction {
    private static final Logger logger = Logger.getLogger(XMLDBDefragment.class);

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("defragment", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                    "Start a defragmentation run on each document which has a node in $nodes. " +
                    "Fragmentation may occur if nodes are inserted into a document using XQuery update " +
                    "extensions. " +
                    "The second argument specifies the minimum number of fragmented pages which should " +
                    "be in a document before it is considered for defragmentation. " +
                    "Please note that defragmenting a document changes its internal structure, so any " +
                    "references to this document will become invalid, in particular, variables pointing to " +
                    "some nodes in the document.",
                    new SequenceType[] {
			new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ONE_OR_MORE, "The sequence of nodes from the documents to defragment"),
			new FunctionParameterSequenceType("integer", Type.INTEGER, Cardinality.EXACTLY_ONE, "The minimum number of fragmented pages required before defragmenting")
                    },
                    new SequenceType(Type.ITEM, Cardinality.EMPTY)),
            new FunctionSignature(
                    new QName("defragment", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
                    "Start a defragmentation run on each document which has a node in $nodes. " +
                    "Fragmentation may occur if nodes are inserted into a document using XQuery update " +
                    "extensions. " +
                    "Please note that defragmenting a document changes its internal structure, so any " +
                    "references to this document will become invalid, in particular, variables pointing to " +
                    "some nodes in the document.",
                    new SequenceType[] {
			new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ONE_OR_MORE, "The sequence of nodes from the documents to defragment"),
                    },
                    new SequenceType(Type.ITEM, Cardinality.EMPTY))
    };

    public XMLDBDefragment(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        int splitCount = ((IntegerValue)args[1].itemAt(0)).getInt();
        NodeSet nodes = args[0].toNodeSet();
        DocumentSet docs = nodes.getDocumentSet();
        try {
            Modification.checkFragmentation(context, docs, splitCount);
        } catch (EXistException e) {
            logger.error("An error occurred while defragmenting documents: " + e.getMessage());
            throw new XPathException("An error occurred while defragmenting documents: " + e.getMessage(), e);
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
