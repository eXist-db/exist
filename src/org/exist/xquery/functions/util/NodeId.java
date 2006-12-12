/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.xquery.functions.util;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
public class NodeId extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("node-id", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the internal node-id of a node. The internal node-id uniquely identifies " +
			"a node within its document. It is encoded as a long number.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
			},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	/**
	 * @param context
	 */
	public NodeId(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		NodeValue docNode =(NodeValue) args[0].itemAt(0);
		if (docNode.getImplementationType() == NodeValue.IN_MEMORY_NODE)
			throw new XPathException(getASTNode(), "First argument to function node-by-id " +
					"should be a persistent node, not an in-memory node.");
		org.exist.numbering.NodeId nodeId = ((NodeProxy)docNode).getNodeId();
		return new StringValue(nodeId.toString());
	}
}
