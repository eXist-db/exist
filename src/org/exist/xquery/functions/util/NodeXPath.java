package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;


public class NodeXPath extends Function
{
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("node-xpath", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the XPath for a Node.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE),
				},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
	
	public NodeXPath(XQueryContext context)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if(seq.isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		
		NodeValue nv = (NodeValue)seq.itemAt(0);
		Node n = nv.getNode();

		//if at the document level just return /
		if(n.getNodeType() == Node.DOCUMENT_NODE)
			return new StringValue("/");
		
		/* walk up the node hierarchy
		 * - node names become path names 
		 * - attributes become predicates
		 */
        // TODO DIZZZZ Java5 remove toString()
        StringBuffer buf = new StringBuffer(nodeToXPath(n).toString());
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
	 * @return StringBuffer containing the XPath
	 */
	public StringBuffer nodeToXPath(Node n)
	{
		StringBuffer xpath = new StringBuffer("/" + getFullNodeName(n));
		
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
