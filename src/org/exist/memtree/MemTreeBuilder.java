/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.memtree;

import java.util.Arrays;

import org.exist.dom.QName;
import org.exist.xpath.StaticContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;

/**
 * Use this class to build a new in-memory DOM document.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class MemTreeBuilder {

	protected DocumentImpl doc;
	protected short level = 1;
	protected int[] prevNodeInLevel;
	protected StaticContext context = null;
	
	public MemTreeBuilder() {
		this(null);
	}
	
	public MemTreeBuilder(StaticContext context) {
		super();
		this.context = context;
		prevNodeInLevel = new int[15];
		Arrays.fill(prevNodeInLevel, -1);
		prevNodeInLevel[0] = 0;
	}

	/**
	 * Returns the created document object.
	 * 
	 * @return
	 */
	public Document getDocument() {
		return doc;
	}

	/**
	 * Start building the document.
	 */
	public void startDocument() {
		this.doc = new DocumentImpl(500, 50, 1000);
	}

	/**
	 * End building the document.
	 */
	public void endDocument() {
	}

	/**
	 * Create a new element.
	 * 
	 * @return the node number of the created element
	 */
	public int startElement(
		String namespaceURI,
		String localName,
		String qname,
		Attributes attributes) {
		int p = qname.indexOf(':');
		String prefix = null;
		if(context != null) {
			prefix = context.getPrefixForURI(namespaceURI);
		}
		if(prefix == null)
			prefix = p > -1 ? qname.substring(0, p) : "";
		QName qn = new QName(localName, namespaceURI, prefix);
		return startElement(qn, attributes);
	}

	/**
	 * Create a new element.
	 * 
	 * @return the node number of the created element
	 */
	public int startElement(QName qn, Attributes attributes) {
		int nodeNr = doc.addNode(Node.ELEMENT_NODE, level, qn);
		// parse attributes
		String attrPrefix;
		String attrLocalName;
		String attrNS;
		String attrQName;
		int p;
		for (int i = 0; i < attributes.getLength(); i++) {
			attrNS = attributes.getURI(i);
			attrLocalName = attributes.getLocalName(i);
			attrQName = attributes.getQName(i);
			// skip xmlns-attributes and attributes in eXist's namespace
			if (!(attrQName.startsWith("xmlns")
				|| attrNS.equals("http://exist.sourceforge.net/NS/exist"))) {
				p = attrQName.indexOf(':');
				attrPrefix = (p > -1) ? attrQName.substring(0, p) : null;
				p =
					doc.addAttribute(
						nodeNr,
						new QName(attrLocalName, attrNS, attrPrefix),
						attributes.getValue(i));
			}
		}

		// update links
		if (level + 1 >= prevNodeInLevel.length) {
			int[] t = new int[level + 2];
			System.arraycopy(prevNodeInLevel, 0, t, 0, level);
			prevNodeInLevel = t;
		}
		int prevNr = prevNodeInLevel[level];
		if (prevNr > -1)
			doc.next[prevNr] = nodeNr;
		doc.next[nodeNr] = prevNodeInLevel[level - 1];
		prevNodeInLevel[level] = nodeNr;
		++level;
		return nodeNr;
	}

	/**
	 * Close the last element created.
	 */
	public void endElement() {
		prevNodeInLevel[level] = -1;
		--level;
	}

	public void addAttribute(QName qname, String value) {
		int lastNode = doc.getLastNode();
		if(doc.nodeKind[lastNode] != Node.ELEMENT_NODE) {
			System.out.println("appending attribute as text; last = " + doc.nodeKind[lastNode]);
			characters(value);
		} else {
			doc.addAttribute(lastNode, qname, value);
		}
	}
	
	/**
	 * Create a new text node.
	 * 
	 * @return the node number of the created node
	 */
	public int characters(char[] ch, int start, int len) {
		int nodeNr = doc.addNode(Node.TEXT_NODE, level, null);
		doc.addChars(nodeNr, ch, start, len);
		int prevNr = prevNodeInLevel[level];
		if (prevNr > -1)
			doc.next[prevNr] = nodeNr;
		doc.next[nodeNr] = prevNodeInLevel[level - 1];
		prevNodeInLevel[level] = nodeNr;
		return nodeNr;
	}

	/**
	 * Create a new text node.
	 * 
	 * @return the node number of the created node
	 */
	public int characters(CharSequence s) {
		int nodeNr = doc.addNode(Node.TEXT_NODE, level, null);
		doc.addChars(nodeNr, s);
		int prevNr = prevNodeInLevel[level];
		if (prevNr > -1)
			doc.next[prevNr] = nodeNr;
		doc.next[nodeNr] = prevNodeInLevel[level - 1];
		prevNodeInLevel[level] = nodeNr;
		return nodeNr;
	}
	
	public int comment(CharSequence data) {
		int nodeNr = doc.addNode(Node.COMMENT_NODE, level, null);
		doc.addChars(nodeNr, data);
		int prevNr = prevNodeInLevel[level];
		if (prevNr > -1)
			doc.next[prevNr] = nodeNr;
		doc.next[nodeNr] = prevNodeInLevel[level - 1];
		prevNodeInLevel[level] = nodeNr;
		return nodeNr;
	}
	
	public int processingInstruction(String target, String data) {
		QName qn = new QName(target, null, null);
		int nodeNr = doc.addNode(Node.PROCESSING_INSTRUCTION_NODE, level, qn);
		doc.addChars(nodeNr, data);
		int prevNr = prevNodeInLevel[level];
		if (prevNr > -1)
			doc.next[prevNr] = nodeNr;
		doc.next[nodeNr] = prevNodeInLevel[level - 1];
		prevNodeInLevel[level] = nodeNr;
		return nodeNr;
	}
}
