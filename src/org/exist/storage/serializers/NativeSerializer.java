/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
 *  $Id:
 */
package org.exist.storage.serializers;

import java.util.*;

import org.apache.oro.text.perl.Perl5Util;
import org.dbxml.core.data.Value;
import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.XMLUtil;
import org.w3c.dom.*;
import org.xml.sax.*;
import org.xml.sax.helpers.*;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    13. April 2002
 */
public class NativeSerializer extends Serializer {

	private boolean showId = false;
	private Perl5Util reutil = new Perl5Util();
	
	/**
	 *  Constructor for the NativeSerializer object
	 *
	 *@param  broker  Description of the Parameter
	 *@param  pool    Description of the Parameter
	 */
	public NativeSerializer(DBBroker broker, Configuration config) {
		super(broker, config);
		String showIdParam =
			(String) config.getProperty("serialization.add-exist-id");
		if (showIdParam != null)
			showId = showIdParam.equalsIgnoreCase("true");
	}

	/**
	 *  Description of the Method
	 *
	 *@param  set               Description of the Parameter
	 *@param  start             Description of the Parameter
	 *@param  howmany           Description of the Parameter
	 *@param  queryTime         Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(
		NodeSet set,
		int start,
		int howmany,
		long queryTime)
		throws SAXException {
		Iterator iter = set.iterator();
		for (int i = 0; i < start - 1; i++)
			iter.next();

		if (!iter.hasNext())
			return;
		contentHandler.startDocument();
		contentHandler.startPrefixMapping(
			"exist",
			EXIST_NS);
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute(
			"",
			"hitCount",
			"hitCount",
			"CDATA",
			Integer.toString(set.getLength()));
		if (queryTime >= 0)
			attribs.addAttribute(
				"",
				"queryTime",
				"queryTime",
				"CDATA",
				Long.toString(queryTime));

		contentHandler.startElement(
			EXIST_NS,
			"result",
			"exist:result",
			attribs);
		NodeProxy p;
		long startTime = System.currentTimeMillis();
		Iterator domIter;
		for (int i = 0; i < howmany && iter.hasNext(); i++) {
			p = (NodeProxy) iter.next();
			if (p == null)
				continue;
			domIter = broker.getDOMIterator(p);
			if (domIter == null)
				continue;
			serializeToSAX(null, domIter, p.doc, p.gid, true, p.matches);
		}
		contentHandler.endElement(
			EXIST_NS,
			"result",
			"exist:result");
		contentHandler.endDocument();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc               Description of the Parameter
	 *@param  generateDocEvent  Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(Document doc, boolean generateDocEvent)
		throws SAXException {
		long start = System.currentTimeMillis();
		setDocument((DocumentImpl) doc);
		NodeList children = doc.getChildNodes();
		if (generateDocEvent)
			contentHandler.startDocument();

		contentHandler.startPrefixMapping(
			"exist",
			EXIST_NS);
		// iterate through children
		for (int i = 0; i < children.getLength(); i++) {
			final NodeImpl n = (NodeImpl) children.item(i);
			final NodeProxy p =
				new NodeProxy(
					(DocumentImpl) n.getOwnerDocument(),
					n.getGID(),
					n.getInternalAddress());
			Iterator domIter = broker.getDOMIterator(p);
			domIter.next();
			serializeToSAX(
				n,
				domIter,
				(DocumentImpl) n.getOwnerDocument(),
				n.getGID(),
				false,
				null);
		}
		LOG.debug(
			"serializing document "
				+ ((DocumentImpl) doc).getDocId()
				+ "to SAX took "
				+ (System.currentTimeMillis() - start));
		contentHandler.endPrefixMapping("exist");
		if (generateDocEvent)
			contentHandler.endDocument();

	}

	/**
	 *  Description of the Method
	 *
	 *@param  n                 Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(Node n) throws SAXException {
		if (!(n instanceof NodeImpl))
			throw new RuntimeException("wrong implementation");
		serializeToSAX(
			new NodeProxy(
				(DocumentImpl) n.getOwnerDocument(),
				((NodeImpl) n).getGID()));
	}

	/**
	 *  Description of the Method
	 *
	 *@param  p                 Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(NodeProxy p) throws SAXException {
		serializeToSAX(p, true);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  p                  Description of the Parameter
	 *@param  generateDocEvents  Description of the Parameter
	 *@exception  SAXException   Description of the Exception
	 */
	protected void serializeToSAX(NodeProxy p, boolean generateDocEvents)
		throws SAXException {
		if (generateDocEvents)
			contentHandler.startDocument();

		contentHandler.startPrefixMapping(
			"exist",
			EXIST_NS);
		Iterator domIter = broker.getDOMIterator(p);
		serializeToSAX(null, domIter, p.doc, p.gid, true, p.matches);
		contentHandler.endPrefixMapping("exist");
		if (generateDocEvents)
			contentHandler.endDocument();

	}

	/**
	 *  Description of the Method
	 *
	 *@param  iter              Description of the Parameter
	 *@param  doc               Description of the Parameter
	 *@param  gid               Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(Iterator iter, DocumentImpl doc, long gid)
		throws SAXException {
		serializeToSAX(null, iter, doc, gid, true, null);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  node              Description of the Parameter
	 *@param  iter              Description of the Parameter
	 *@param  doc               Description of the Parameter
	 *@param  gid               Description of the Parameter
	 *@param  first             Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(
		NodeImpl node,
		Iterator iter,
		DocumentImpl doc,
		long gid,
		boolean first,
		Match matches[])
		throws SAXException {
		serializeToSAX(node, iter, doc, gid, first, new ArrayList(), matches);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  node              Description of the Parameter
	 *@param  iter              Description of the Parameter
	 *@param  doc               Description of the Parameter
	 *@param  gid               Description of the Parameter
	 *@param  first             Description of the Parameter
	 *@param  prefixes          Description of the Parameter
	 *@exception  SAXException  Description of the Exception
	 */
	protected void serializeToSAX(
		NodeImpl node,
		Iterator iter,
		DocumentImpl doc,
		long gid,
		boolean first,
		ArrayList prefixes,
		Match matches[])
		throws SAXException {
		setDocument(doc);
		if (node == null) {
			Value value = (Value) iter.next();
			if (value != null) {
				node = NodeImpl.deserialize(value.getData(), doc);
				node.setOwnerDocument(doc);
			}
		}
		if (node == null)
			return;
		char ch[];
		String cdata;
		switch (node.getNodeType()) {
			case Node.ELEMENT_NODE :
				int children = node.getChildCount();
				int count = 0;
				int childLen;
				NodeImpl child = null;
				AttributesImpl attributes = new AttributesImpl();
				if (first || showId) {
					attributes.addAttribute(
						EXIST_NS,
						"id",
						"exist:id",
						"CDATA",
						Long.toString(gid));
				}
				if (first) {
					attributes.addAttribute(
						EXIST_NS,
						"source",
						"exist:source",
						"CDATA",
						doc.getFileName());
				}
				if (children > 0)
					gid = XMLUtil.getFirstChildId(doc, gid);
				while (count < children) {
					Value value = (Value) iter.next();
					child = NodeImpl.deserialize(value.getData(), doc);
					child.setOwnerDocument(doc);
					if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
						if((highlightMatches & TAG_ATTRIBUTE_MATCHES) > 0)
							cdata = processText(((AttrImpl) child).getValue(), gid, matches);
						else
							cdata = ((AttrImpl)child).getValue();
						attributes.addAttribute(
							child.getNamespaceURI(),
							child.getLocalName(),
							child.getNodeName(),
							"CDATA",
							cdata);
						count++;
						gid++;
					} else
						break;
				}
				ArrayList myPrefixes = null;
				String defaultNS = null;
				if (((ElementImpl) node).declaresNamespacePrefixes()) {
					// declare namespaces used by this element
					String prefix;
					myPrefixes = new ArrayList();
					for (Iterator i =
						((ElementImpl) node).getNamespacePrefixes();
						i.hasNext();
						) {
						prefix = (String) i.next();
						if (!prefixes.contains(prefix)) {
							if (prefix.startsWith("#")) {
								defaultNS = broker.getNamespaceURI(prefix);
								contentHandler.startPrefixMapping(
									"",
									defaultNS);
							} else
								contentHandler.startPrefixMapping(
									prefix,
									broker.getNamespaceURI(prefix));

							prefixes.add(prefix);
							myPrefixes.add(prefix);
						}
					}
				}
				String ns =
					defaultNS == null ? node.getNamespaceURI() : defaultNS;
				contentHandler.startElement(
					ns,
					node.getLocalName(),
					node.getNodeName(),
					attributes);
				while (count < children) {
					serializeToSAX(
						child,
						iter,
						doc,
						gid++,
						false,
						prefixes,
						matches);
					if (++count < children) {
						Value value = (Value) iter.next();
						child = NodeImpl.deserialize(value.getData(), doc);
						child.setOwnerDocument(doc);
					} else
						break;
				}
				contentHandler.endElement(
					ns,
					node.getLocalName(),
					node.getNodeName());
				if (((ElementImpl) node).declaresNamespacePrefixes()
					&& myPrefixes != null) {
					String prefix;
					for (Iterator i = myPrefixes.iterator(); i.hasNext();) {
						prefix = (String) i.next();
						contentHandler.endPrefixMapping(prefix);
						prefixes.remove(prefix);
					}
				}

				break;
			case Node.TEXT_NODE :
				if (first && createContainerElements) {
					AttributesImpl attribs = new AttributesImpl();
					attribs.addAttribute(
						EXIST_NS,
						"id",
						"exist:id",
						"CDATA",
						Long.toString(gid));
					attribs.addAttribute(
						EXIST_NS,
						"source",
						"exist:source",
						"CDATA",
						doc.getFileName());
					contentHandler.startElement(
						EXIST_NS,
						"text",
						"exist:text",
						attribs);
				}
				if((highlightMatches & TAG_ELEMENT_MATCHES) == TAG_ELEMENT_MATCHES)
					cdata = processText(((Text) node).getData(), gid, matches);
				else
					cdata = ((Text)node).getData();
				if(cdata.indexOf('|') > -1)
					scanText(cdata);
				else {
					ch = new char[cdata.length()];
					cdata.getChars(0, cdata.length(), ch, 0);
					contentHandler.characters(ch, 0, ch.length);
				}
				if (first && createContainerElements)
					contentHandler.endElement(
						EXIST_NS,
						"text",
						"exist:text");

				break;
			case Node.ATTRIBUTE_NODE : 
				if (first && createContainerElements) {
					AttributesImpl attribs = new AttributesImpl();
					attribs.addAttribute(
						EXIST_NS,
						"id",
						"exist:id",
						"CDATA",
						Long.toString(gid));
					attribs.addAttribute(
						EXIST_NS,
						"source",
						"exist:source",
						"CDATA",
						doc.getFileName());
					if((highlightMatches & TAG_ATTRIBUTE_MATCHES) > 0)
						cdata = processText(((AttrImpl) node).getValue(), gid, matches);
					else
						cdata = ((AttrImpl)node).getValue();
					attribs.addAttribute(
						node.getNamespaceURI(),
						node.getLocalName(),
						node.getNodeName(),
						"CDATA",
						cdata);
					contentHandler.startElement(
						EXIST_NS,
						"attribute",
						"exist:attribute",
						attribs);
					contentHandler.endElement(
						EXIST_NS,
						"attribute",
						"exist:attribute");
				} else {
					if((highlightMatches & TAG_ATTRIBUTE_MATCHES) == TAG_ATTRIBUTE_MATCHES)
						cdata = processText(((AttrImpl) node).getValue(), gid, matches);
					else
						cdata = ((AttrImpl)node).getValue();
					ch = new char[cdata.length()];
					cdata.getChars(0, ch.length, ch, 0);
					contentHandler.characters(ch, 0, ch.length);
				}
				break;
			case Node.PROCESSING_INSTRUCTION_NODE :
				contentHandler.processingInstruction(
					((ProcessingInstructionImpl) node).getTarget(),
					((ProcessingInstructionImpl) node).getData());
				break;
			case Node.COMMENT_NODE :
				if (lexicalHandler != null) {
					String comment = ((CommentImpl) node).getData();
					char data[] = new char[comment.length()];
					comment.getChars(0, data.length - 1, data, 0);
					lexicalHandler.comment(data, 0, data.length - 1);
				}
				break;
		}
	}

	private final String processText(String data, long gid, Match matches[]) {
		if(matches == null)
			return data;
		// sort to get longest string first
		Arrays.sort(matches);
		// prepare a regular expression to mark match-terms
		StringBuffer expr = null;
		for (int i = 0; i < matches.length; i++)
			if (matches[i].getNodeId() == gid) {
				if(expr == null) {
					expr = new StringBuffer();
					expr.append("s/\\b(");
				}
				if(expr.length() > 5)
					expr.append('|');
				expr.append(matches[i].getMatchingTerm());
			}
		if(expr != null) {
			expr.append(")\\b/||$1||/gi");
			data = reutil.substitute(expr.toString(), data);
		}
		return data;
	}
	
	private final void scanText(String data) throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		int p0 = 0, p1;
		boolean inTerm = false;
		while(p0 < data.length()) {
			p1 = data.indexOf("||", p0);
			if(p1 < 0) {
				outputText(data.substring(p0));
				break;
			}
			if(inTerm) {
				contentHandler.startElement(EXIST_NS,
					"match", "exist:match", atts);
				outputText(data.substring(p0, p1));
				contentHandler.endElement(EXIST_NS,
					"match", "exist:match");
				inTerm = false;
			} else {
				inTerm = true;
				outputText(data.substring(p0, p1));
			}
			p0 = p1 + 2;
		}
	}
	
	private final void outputText(String data) throws SAXException {
		final char ch[] = new char[data.length()];
		data.getChars(0, ch.length, ch, 0);
		contentHandler.characters(ch, 0, ch.length);
	}
}
