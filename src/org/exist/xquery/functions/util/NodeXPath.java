package org.exist.xquery.functions.util;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;


public class NodeXPath extends Function
{
	protected static final Logger logger = Logger.getLogger(NodeXPath.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("node-xpath", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the XPath for a Node.",
			new SequenceType[] {
				new FunctionParameterSequenceType("node", Type.NODE, Cardinality.EXACTLY_ONE, "the node to retrieve the XPath to"),
				},
			new FunctionParameterSequenceType("xpath-expression", Type.STRING, Cardinality.ZERO_OR_ONE, "the XPath expression of the node"));
	
	public NodeXPath(XQueryContext context)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		
		NodeValue nv = (NodeValue)seq.itemAt(0);
		Node n = nv.getNode();

		//if at the document level just return /
		if(n.getNodeType() == Node.DOCUMENT_NODE) {
			return new StringValue("/");
		}
		
		/* walk up the node hierarchy
		 * - node names become path names 
		 * - attributes become predicates
		 */
        StringBuilder buf = new StringBuilder(nodeToXPath(n));
		while((n = n.getParentNode()) != null)
		{
			if(n.getNodeType() == Node.ELEMENT_NODE)
			{
				buf.insert(0, nodeToXPath(n));
			}
		}
		
		return new StringValue(buf.toString());
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
		StringBuilder xpath = new StringBuilder("/" + getFullNodeName(n));
		
		NamedNodeMap attrs = n.getAttributes();
		for(int i = 0; i < attrs.getLength(); i++)
		{
			Node attr = attrs.item(i);
			xpath.append("[@" + getFullNodeName(attr) + " eq \"" + attr.getNodeValue() + "\"]");
		}
		
		return xpath;
	}
	
	/**
	 * Returns the full node name including the prefix if present
	 * 
	 * @param n The node to get the name for
	 * @return The full name of the node
	 */
	public String getFullNodeName(Node n)
	{
		return n.getPrefix() != null && !n.getPrefix().equals("") ? n.getPrefix() + ":" + n.getLocalName() : n.getLocalName();
	}

}
