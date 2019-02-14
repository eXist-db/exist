/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Attr;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;


public class NodeXPath extends Function
{
	protected static final Logger logger = LogManager.getLogger(NodeXPath.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("node-xpath", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the XPath for a Node.",
			new SequenceType[] {
				new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE, "The node to retrieve the XPath to"),
				},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the XPath expression of the node"));
	
	public NodeXPath(XQueryContext context)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		
		final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		
		final NodeValue nv = (NodeValue)seq.itemAt(0);
		Node n = nv.getNode();

		//if at the document level just return /
		if(n.getNodeType() == Node.DOCUMENT_NODE) {
			return new StringValue("/");
		}
		
		/* walk up the node hierarchy
		 * - node names become path names 
		 * - attributes become predicates
		 */
        final StringBuilder buf = new StringBuilder(nodeToXPath(n));
		while((n = getParent(n)) != null)
		{
			if(n.getNodeType() == Node.ELEMENT_NODE)
			{
				buf.insert(0, nodeToXPath(n));
			}
		}
		
		return new StringValue(buf.toString());
	}

	private Node getParent(final Node n) {
		if (n == null) {
			return null;
		} else if (n instanceof Attr) {
			return ((Attr) n).getOwnerElement();
		} else {
			return n.getParentNode();
		}
	}
	
	/**
	 * Creates an XPath for a Node
	 * The nodes attribute's become predicates
	 * 
	 * @param n The Node to generate an XPath for
	 * @return StringBuilder containing the XPath
	 */
	public StringBuilder nodeToXPath(Node n)
	{
		final StringBuilder xpath = new StringBuilder("/" + getFullNodeName(n));
		
		final NamedNodeMap attrs = n.getAttributes();
		for(int i = 0; i < attrs.getLength(); i++)
		{
			final Node attr = attrs.item(i);
			xpath.append("[@").append(getFullNodeName(attr)).append(" eq \"").append(attr.getNodeValue()).append("\"]");
		}
		
		return xpath;
	}
	
	/**
	 * Returns the full node name including the prefix if present
	 * 
	 * @param n The node to get the name for
	 * @return The full name of the node
	 */
	public String getFullNodeName(final Node n)
	{
		return n.getPrefix() != null && !XMLConstants.DEFAULT_NS_PREFIX.equals(n.getPrefix()) ? n.getPrefix() + ":" + n.getLocalName() : n.getLocalName();
	}

}
