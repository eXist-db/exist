/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xpath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.StringValue;

/**
 * Node constructor for attribute nodes.
 * 
 * @author wolf
 */
public class AttributeConstructor extends NodeConstructor {

	String qname;
	List contents = new ArrayList();
	boolean isNamespaceDecl = false;
	
	public AttributeConstructor(StaticContext context, String name) {
		super(context);
		if(name.startsWith("xmlns"))
			isNamespaceDecl = true;
		this.qname = name;
	}
	
	public void addValue(String value) {
		contents.add(value);
	}
	
	public void addEnclosedExpr(Expression expr) throws XPathException {
		if(isNamespaceDecl)
			throw new XPathException("enclosed expressions are not allowed in namespace " +
				"declaration attributes");
		contents.add(expr);
	}
	
	public String getQName() {
		return qname;
	}
	
	public boolean isNamespaceDeclaration() {
		return isNamespaceDecl;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		StringBuffer buf = new StringBuffer();
		Object next;
		for(Iterator i = contents.iterator(); i.hasNext(); ) {
			next = i.next();
			if(next instanceof Expression)
				evalEnclosedExpr(((Expression)next).eval(docs, contextSequence, contextItem), buf);
			else
				buf.append(next);
		}
		return new StringValue(buf.toString());
	}

	private void evalEnclosedExpr(Sequence seq, StringBuffer buf) throws XPathException {
		Item item;
		AtomicValue atomic;
		int count = 0;
		for(SequenceIterator i = seq.iterate(); i.hasNext(); count++) {
			item = i.nextItem();
			atomic = item.atomize();
			if(count > 0 && i.hasNext())
				buf.append(' ');
			buf.append(atomic.getStringValue());
		}
	}
	
	/**
	 * If this is a namespace declaration attribute, return
	 * the single string value of the attribute.
	 * 
	 * @return
	 */
	public String getLiteralValue() {
		if(contents.size() == 0)
			return "";
		return (String)contents.get(0);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(qname).append("=\"");
		Object next;
		for(Iterator i = contents.iterator(); i.hasNext(); ) {
			next = i.next();
			if(next instanceof Expression)
				buf.append(((Expression)next).pprint());
			else
				buf.append(next);
		}
		buf.append('"');
		return buf.toString();
	}

}
