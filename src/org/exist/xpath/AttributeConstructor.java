/*
 * Created on Oct 22, 2003
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.xpath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.StringValue;

public class AttributeConstructor extends NodeConstructor {

	String qname;
	List contents = new ArrayList();
	boolean isNamespaceDecl = false;
	
	public AttributeConstructor(String name) {
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
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		StringBuffer buf = new StringBuffer();
		Object next;
		for(Iterator i = contents.iterator(); i.hasNext(); ) {
			next = i.next();
			if(next instanceof Expression)
				evalEnclosedExpr(((Expression)next).eval(context, docs, contextSequence, contextItem), buf);
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
