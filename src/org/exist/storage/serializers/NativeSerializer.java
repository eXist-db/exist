/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.oro.text.perl.Perl5Util;
import org.exist.dom.AttrImpl;
import org.exist.dom.CommentImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.Match;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.TextImpl;
import org.exist.dom.XMLUtil;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class NativeSerializer extends Serializer {

	public final static int EXIST_ID_NONE = 0;
	public final static int EXIST_ID_ELEMENT = 1;
	public final static int EXIST_ID_ALL = 2;

	private int showId = EXIST_ID_ELEMENT;

	private Perl5Util reutil = new Perl5Util();

	public NativeSerializer(DBBroker broker, Configuration config) {
		super(broker, config);
		String showIdParam = (String) config.getProperty("serialization.add-exist-id");
		if (showIdParam != null) {
			if (showIdParam.equals("element"))
				showId = EXIST_ID_ELEMENT;
			else if (showIdParam.equals("all"))
				showId = EXIST_ID_ALL;
			else
				showId = EXIST_ID_NONE;
		}
	}

	protected void serializeToSAX(NodeSet set, int start, int howmany, long queryTime)
		throws SAXException {
		boolean generateDocEvents = 
			outputProperties.getProperty(Serializer.GENERATE_DOC_EVENTS, "false").equals("true");
		Iterator iter = set.iterator();
		for (int i = 0; i < start - 1; i++)
			iter.next();

		if (!iter.hasNext())
			return;
		if(generateDocEvents)
			contentHandler.startDocument();
		contentHandler.startPrefixMapping("exist", EXIST_NS);
		AttributesImpl attribs = new AttributesImpl();
		attribs.addAttribute(
			"",
			"hitCount",
			"hitCount",
			"CDATA",
			Integer.toString(set.getLength()));
		if (queryTime >= 0)
			attribs.addAttribute("", "queryTime", "queryTime", "CDATA", Long.toString(queryTime));

		contentHandler.startElement(EXIST_NS, "result", "exist:result", attribs);
		NodeProxy p;
		long startTime = System.currentTimeMillis();
		Iterator domIter;
		for (int i = 0; i < howmany && iter.hasNext(); i++) {
			p = (NodeProxy) iter.next();
			if (p == null)
				continue;
			domIter = broker.getNodeIterator(p);
			if (domIter == null)
				continue;
			serializeToSAX(null, domIter, p.doc, p.gid, true, p.match);
		}
		contentHandler.endElement(EXIST_NS, "result", "exist:result");
		if(generateDocEvents)
			contentHandler.endDocument();
	}

	protected void serializeToSAX(Document doc, boolean generateDocEvent) throws SAXException {
		long start = System.currentTimeMillis();
		setDocument((DocumentImpl) doc);
		NodeList children = doc.getChildNodes();
		if (generateDocEvent)
			contentHandler.startDocument();

		//contentHandler.startPrefixMapping("exist", EXIST_NS);
		// iterate through children
		for (int i = 0; i < children.getLength(); i++) {
			final NodeImpl n = (NodeImpl) children.item(i);
			final NodeProxy p =
				new NodeProxy(
					(DocumentImpl) n.getOwnerDocument(),
					n.getGID(),
					n.getInternalAddress());
			Iterator domIter = broker.getNodeIterator(p);
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
		//contentHandler.endPrefixMapping("exist");
		if (generateDocEvent)
			contentHandler.endDocument();

	}

	protected void serializeToSAX(Node n) throws SAXException {
		if (!(n instanceof NodeImpl))
			throw new RuntimeException("wrong implementation");
		serializeToSAX(new NodeProxy((DocumentImpl) n.getOwnerDocument(), ((NodeImpl) n).getGID()));
	}

	protected void serializeToSAX(NodeProxy p) throws SAXException {
		serializeToSAX(p, true);
	}

	protected void serializeToSAX(NodeProxy p, boolean generateDocEvents) throws SAXException {
		if (generateDocEvents)
			contentHandler.startDocument();

		contentHandler.startPrefixMapping("exist", EXIST_NS);
		Iterator domIter = broker.getNodeIterator(p);
		serializeToSAX(null, domIter, p.doc, p.gid, true, p.match);
		contentHandler.endPrefixMapping("exist");
		if (generateDocEvents)
			contentHandler.endDocument();

	}
	
	protected void serializeToSAX(Iterator iter, DocumentImpl doc, long gid) throws SAXException {
		serializeToSAX(null, iter, doc, gid, true, null);
	}

	protected void serializeToSAX(
		NodeImpl node,
		Iterator iter,
		DocumentImpl doc,
		long gid,
		boolean first,
		Match match)
		throws SAXException {
		serializeToSAX(node, iter, doc, gid, first, new TreeSet(), match);
	}

	protected void serializeToSAX(
		NodeImpl node,
		Iterator iter,
		DocumentImpl doc,
		long gid,
		boolean first,
		Set namespaces,
		Match match)
		throws SAXException {
		setDocument(doc);
		if (node == null)
			node = (NodeImpl) iter.next();
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
				if ((first && showId == EXIST_ID_ELEMENT) || showId == EXIST_ID_ALL) {
					attributes.addAttribute(
						EXIST_NS,
						"id",
						"exist:id",
						"CDATA",
						Long.toString(gid));
				}
				if (first && showId > 0) {
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
					child = (NodeImpl) iter.next();
					if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
						if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) > 0)
							cdata = processAttribute(((AttrImpl) child).getValue(), gid, match);
						else
							cdata = ((AttrImpl) child).getValue();
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
				String defaultNS = null;
				if (((ElementImpl) node).declaresNamespacePrefixes()) {
					// declare namespaces used by this element
					String prefix, uri;
					for (Iterator i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
						prefix = (String) i.next();
						if (prefix.length() == 0) {
							defaultNS = ((ElementImpl) node).getNamespaceForPrefix(prefix);
							contentHandler.startPrefixMapping("", defaultNS);
							namespaces.add(defaultNS);
						} else {
							uri = ((ElementImpl) node).getNamespaceForPrefix(prefix);
							if (uri.equals(EXIST_NS))
								continue;
							contentHandler.startPrefixMapping(prefix, uri);
							namespaces.add(uri);
						}
					}
				}
				String ns = defaultNS == null ? node.getNamespaceURI() : defaultNS;
				if(ns.length() > 0 && (!namespaces.contains(ns)))
					contentHandler.startPrefixMapping(node.getPrefix(), ns);
				contentHandler.startElement(
					ns,
					node.getLocalName(),
					node.getNodeName(),
					attributes);
				while (count < children) {
					serializeToSAX(child, iter, doc, gid++, false, namespaces, match);
					if (++count < children) {
						child = (NodeImpl) iter.next();
					} else
						break;
				}
				contentHandler.endElement(ns, node.getLocalName(), node.getNodeName());
				if (((ElementImpl) node).declaresNamespacePrefixes()) {
					String prefix;
					for (Iterator i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
						prefix = (String) i.next();
						contentHandler.endPrefixMapping(prefix);
					}
				}
				if(ns.length() > 0 && (!namespaces.contains(ns)))
					contentHandler.endPrefixMapping(node.getPrefix());
				break;
			case Node.TEXT_NODE :
				if (first && createContainerElements) {
					AttributesImpl attribs = new AttributesImpl();
					if (showId > 0) {
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
					}
					contentHandler.startElement(EXIST_NS, "text", "exist:text", attribs);
				}
				if ((getHighlightingMode() & TAG_ELEMENT_MATCHES) == TAG_ELEMENT_MATCHES
					&& (cdata = processText((TextImpl) node, gid, match)) != null)
					scanText(cdata);
				else {
					((TextImpl) node).getXMLString().toSAX(contentHandler);
				}
				if (first && createContainerElements)
					contentHandler.endElement(EXIST_NS, "text", "exist:text");

				break;
			case Node.ATTRIBUTE_NODE :
				if (first && createContainerElements) {
					AttributesImpl attribs = new AttributesImpl();
					if (showId > 0) {
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
					}
					if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) > 0)
						cdata = processAttribute(((AttrImpl) node).getValue(), gid, match);
					else
						cdata = ((AttrImpl) node).getValue();
					attribs.addAttribute(
						node.getNamespaceURI(),
						node.getLocalName(),
						node.getNodeName(),
						"CDATA",
						cdata);
					contentHandler.startElement(EXIST_NS, "attribute", "exist:attribute", attribs);
					contentHandler.endElement(EXIST_NS, "attribute", "exist:attribute");
				} else {
					if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) == TAG_ATTRIBUTE_MATCHES)
						cdata = processAttribute(((AttrImpl) node).getValue(), gid, match);
					else
						cdata = ((AttrImpl) node).getValue();
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

	private final String processAttribute(String data, long gid, Match match) {
		if (match == null)
			return data;
		// prepare a regular expression to mark match-terms
		StringBuffer expr = null;
		Match next = match;
		while(next != null) {
			if (next.getNodeId() == gid) {
				if (expr == null) {
					expr = new StringBuffer();
					expr.append("s/\\b(");
				}
				if (expr.length() > 5)
					expr.append('|');
				expr.append(next.getMatchingTerm());
			}
			next = next.getNextMatch();
		}
		if (expr != null) {
			expr.append(")\\b/||$1||/gi");
			data = reutil.substitute(expr.toString(), data);
		}
		return data;
	}

	private final String processText(TextImpl text, long gid, Match match) {
		if (match == null)
			return null;
		// prepare a regular expression to mark match-terms
		StringBuffer expr = null;
		Match next = match;
		while(next != null) {
			if (next.getNodeId() == gid) {
				if (expr == null) {
					expr = new StringBuffer();
					expr.append("s/\\b(");
				}
				if (expr.length() > 5)
					expr.append('|');
				expr.append(next.getMatchingTerm());
			}
			next = next.getNextMatch();
		}
		if (expr != null) {
			expr.append(")\\b/||$1||/gi");
			return reutil.substitute(expr.toString(), text.getData());
		} else
			return null;
	}

	private final void scanText(String data) throws SAXException {
		AttributesImpl atts = new AttributesImpl();
		int p0 = 0, p1;
		boolean inTerm = false;
		while (p0 < data.length()) {
			p1 = data.indexOf("||", p0);
			if (p1 < 0) {
				outputText(data.substring(p0));
				break;
			}
			if (inTerm) {
				contentHandler.startElement(EXIST_NS, "match", "exist:match", atts);
				outputText(data.substring(p0, p1));
				contentHandler.endElement(EXIST_NS, "match", "exist:match");
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
