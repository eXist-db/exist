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
import org.w3c.dom.Attr;
import org.w3c.dom.CDATASection;
import org.w3c.dom.Comment;
import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Element;
import org.w3c.dom.EntityReference;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.w3c.dom.Document;

public class DocumentImpl extends NodeImpl implements Document {

	protected short[] nodeKind;
	protected short[] treeLevel;
	protected int[] next;
	protected QName[] nodeName;
	protected int[] alpha;
	protected int[] alphaLen;

	protected char[] characters;
	protected int nextChar = 0;

	protected QName[] attrName;
	protected int[] attrParent;
	protected String[] attrValue;
	protected int nextAttr = 0;
	
	protected int size = 1;
	protected int documentRootNode = -1;

	public DocumentImpl(int nodeSize, int attrSize, int charBufSize) {
		super(null, 0);
		nodeKind = new short[nodeSize];
		treeLevel = new short[nodeSize];
		next = new int[nodeSize];
		Arrays.fill(next, -1);
		nodeName = new QName[nodeSize];
		alpha = new int[nodeSize];
		alphaLen = new int[nodeSize];

		characters = new char[charBufSize];

		attrName = new QName[attrSize];
		attrParent = new int[attrSize];
		attrValue = new String[attrSize];

		treeLevel[0] = 0;
		nodeKind[0] = Node.DOCUMENT_NODE;
		document = this;
	}

	public int addNode(short kind, short level, QName qname) {
		if (size == nodeKind.length)
			grow();
		nodeKind[size] = kind;
		treeLevel[size] = level;
		nodeName[size] = qname;
		alpha[size] = -1; // undefined
		next[size] = -1;
		return size++;
	}

	public void addChars(int nodeNr, char[] ch, int start, int len) {
		if (nextChar + len >= characters.length) {
			int newLen = (characters.length * 3) / 2;
			if(newLen < nextChar + len)
				newLen = nextChar + len;
			char[] nc = new char[newLen];
			System.arraycopy(characters, 0, nc, 0, characters.length);
			characters = nc;
		}
		alpha[nodeNr] = nextChar;
		alphaLen[nodeNr] = len;
		System.arraycopy(ch, start, characters, nextChar, len);
		nextChar += len;
	}

	public void addChars(int nodeNr, CharSequence s) {
		int len = s.length();
		if (nextChar + len >= characters.length) {
			int newLen = (characters.length * 3) / 2;
			if(newLen < nextChar + len)
				newLen = nextChar + len;
			char[] nc = new char[newLen];
			System.arraycopy(characters, 0, nc, 0, characters.length);
			characters = nc;
		}
		alpha[nodeNr] = nextChar;
		alphaLen[nodeNr] = len;
		for(int i = 0; i < len; i++) {
			characters[nextChar++] = s.charAt(i);
		}
	}

	public int addAttribute(int nodeNr, QName qname, String value) {
		if (nextAttr == attrName.length)
			growAttributes();
		attrParent[nextAttr] = nodeNr;
		attrName[nextAttr] = qname;
		attrValue[nextAttr] = value;
		if (alpha[nodeNr] < 0)
			alpha[nodeNr] = nextAttr;
		return nextAttr++;
	}
	
	private void grow() {
		int newSize = (size * 3) / 2;

		short[] newNodeKind = new short[newSize];
		System.arraycopy(nodeKind, 0, newNodeKind, 0, size);
		nodeKind = newNodeKind;

		short[] newTreeLevel = new short[newSize];
		System.arraycopy(treeLevel, 0, newTreeLevel, 0, size);
		treeLevel = newTreeLevel;

		int[] newNext = new int[newSize];
		Arrays.fill(newNext, -1);
		System.arraycopy(next, 0, newNext, 0, size);
		next = newNext;

		QName[] newNodeName = new QName[newSize];
		System.arraycopy(nodeName, 0, newNodeName, 0, size);
		nodeName = newNodeName;

		int[] newAlpha = new int[newSize];
		System.arraycopy(alpha, 0, newAlpha, 0, size);
		alpha = newAlpha;

		int[] newAlphaLen = new int[newSize];
		System.arraycopy(alphaLen, 0, newAlphaLen, 0, size);
		alphaLen = newAlphaLen;
	}

	private void growAttributes() {
		int size = attrName.length;
		int newSize = (size * 3) / 2;

		QName[] newAttrName = new QName[newSize];
		System.arraycopy(attrName, 0, newAttrName, 0, size);
		attrName = newAttrName;

		int[] newAttrParent = new int[newSize];
		System.arraycopy(attrParent, 0, newAttrParent, 0, size);
		attrParent = newAttrParent;

		String[] newAttrValue = new String[newSize];
		System.arraycopy(attrValue, 0, newAttrValue, 0, size);
		attrValue = newAttrValue;
	}

	public NodeImpl getNode(int nodeNr) throws DOMException {
		if (nodeNr == 0)
			return this;
		if (nodeNr >= size)
			throw new DOMException(
				DOMException.HIERARCHY_REQUEST_ERR,
				"node not found");
		NodeImpl node;
		switch (nodeKind[nodeNr]) {
			case Node.ELEMENT_NODE :
				node = new ElementImpl(this, nodeNr);
				break;
			case Node.TEXT_NODE :
				node = new TextImpl(this, nodeNr);
				break;
			case Node.COMMENT_NODE:
				node = new CommentImpl(this, nodeNr);
				break;
			case Node.PROCESSING_INSTRUCTION_NODE:
				node = new ProcessingInstructionImpl(this, nodeNr);
				break;
			default :
				throw new DOMException(
					DOMException.NOT_FOUND_ERR,
					"node not found");
		}
		return node;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#getDoctype()
	 */
	public DocumentType getDoctype() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#getImplementation()
	 */
	public DOMImplementation getImplementation() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#getDocumentElement()
	 */
	public Element getDocumentElement() {
		if (size == 1)
			return null;
		int nodeNr = 1;
		while (nodeKind[nodeNr] != Node.ELEMENT_NODE) {
			if (next[nodeNr] < nodeNr) {
				return null;
			} else
				nodeNr = next[nodeNr];
		}
		return (Element) getNode(nodeNr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		if (size > 1)
			return getNode(1);
		else
			return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createElement(java.lang.String)
	 */
	public Element createElement(String arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createDocumentFragment()
	 */
	public DocumentFragment createDocumentFragment() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createTextNode(java.lang.String)
	 */
	public Text createTextNode(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createComment(java.lang.String)
	 */
	public Comment createComment(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createCDATASection(java.lang.String)
	 */
	public CDATASection createCDATASection(String arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createProcessingInstruction(java.lang.String, java.lang.String)
	 */
	public ProcessingInstruction createProcessingInstruction(
		String arg0,
		String arg1)
		throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createAttribute(java.lang.String)
	 */
	public Attr createAttribute(String arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createEntityReference(java.lang.String)
	 */
	public EntityReference createEntityReference(String arg0)
		throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#getElementsByTagName(java.lang.String)
	 */
	public NodeList getElementsByTagName(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#importNode(org.w3c.dom.Node, boolean)
	 */
	public Node importNode(Node arg0, boolean arg1) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createElementNS(java.lang.String, java.lang.String)
	 */
	public Element createElementNS(String arg0, String arg1)
		throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#createAttributeNS(java.lang.String, java.lang.String)
	 */
	public Attr createAttributeNS(String arg0, String arg1)
		throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#getElementsByTagNameNS(java.lang.String, java.lang.String)
	 */
	public NodeList getElementsByTagNameNS(String arg0, String arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Document#getElementById(java.lang.String)
	 */
	public Element getElementById(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public org.w3c.dom.Document getOwnerDocument() {
		return this;
	}

}
