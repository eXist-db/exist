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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
public class GetNodeById extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(GetNodeById.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("node-by-id", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Retrieves a node by its internal node-id. The document is specified via the first " +
			"argument. It may either be a document node or another node from the same document " +
			"from which the target node will be retrieved by its id. The second argument is " +
			"the internal node-id, specified as a string. If a node with the matching node-id is found, it is returned. " +
			"Otherwise returns the empty sequence.",
			new SequenceType[] {
				new FunctionParameterSequenceType("document", Type.NODE, Cardinality.EXACTLY_ONE, "The document whose node is to be retrieved by its id"),
				new FunctionParameterSequenceType("node-id", Type.STRING, Cardinality.EXACTLY_ONE, "The internal node id")
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_ONE, "the node or an empty sequence if a matching node does not exist"));

	public GetNodeById(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
        final String id = args[1].itemAt(0).getStringValue();
        final NodeId nodeId = context.getBroker().getBrokerPool().getNodeFactory().createFromString(id);
        final NodeValue docNode = (NodeValue) args[0].itemAt(0);
        if (docNode.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
            return ((NodeImpl) docNode).getOwnerDocument().getNodeById(nodeId);
        } else {
            final DocumentImpl doc = ((NodeProxy)docNode).getOwnerDocument();
            final NodeProxy proxy = new NodeProxy(this, doc, nodeId);
            if (proxy.getNode() == null) {
            	return Sequence.EMPTY_SEQUENCE;
			}
            return proxy;
        }
	}
}
