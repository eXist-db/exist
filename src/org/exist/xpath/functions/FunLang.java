/*
 * NativeBroker.java - eXist Open Source Native XML Database
 * Copyright (C) 2001-03 Wolfgang M. Meier
 * meier@ifs.tu-darmstadt.de
 * http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */

package org.exist.xpath.functions;

import java.util.Iterator;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.XMLUtil;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Dependency;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;
import org.w3c.dom.Node;

/**
 * Built-in function fn:lang().
 *
 */
public class FunLang extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("lang", BUILTIN_FUNCTION_NS),
			new SequenceType[] {
				 new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE));

	public FunLang(StaticContext context) {
		super(context, signature);
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		if (contextSequence.getItemType() != Type.NODE)
			return Sequence.EMPTY_SEQUENCE;
		String lang =
			getArgument(0)
				.eval(contextSequence)
				.getStringValue();
		QName qname = new QName("lang", context.getURIForPrefix("xml"), "xml");
		NodeSet attribs = context.getBroker().getAttributesByName(contextSequence.toNodeSet().getDocumentSet(), qname);
		NodeSet temp = new ExtArrayNodeSet();
		NodeProxy p;
		String langValue;
		int hyphen;
		boolean include;
		for (Iterator i = attribs.iterator(); i.hasNext();) {
			include = false;
			p = (NodeProxy) i.next();
			langValue = p.getNodeValue();
			include = lang.equalsIgnoreCase(langValue);
			if (!include) {
				hyphen = langValue.indexOf('-');
				if (hyphen != -1) {
					langValue = langValue.substring(0, hyphen);
					include = lang.equalsIgnoreCase(langValue);
				}
			}
			if (include) {
				p.gid = XMLUtil.getParentId(p.doc, p.gid);
				if (p.gid > -1) {
					p.setNodeType(Node.ELEMENT_NODE);
					temp.add(p);
				}
			}
		}
		if (temp.getLength() > 0) {
			NodeSet result =
				((NodeSet) contextSequence).selectAncestorDescendant(
					temp,
					NodeSet.DESCENDANT,
					true,
					false);
			for (Iterator i = result.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				p.addContextNode(p);
			}
			return result;
		}
		return Sequence.EMPTY_SEQUENCE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}
}
