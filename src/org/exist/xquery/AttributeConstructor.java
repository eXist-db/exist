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
package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Node constructor for attribute nodes.
 * 
 * @author wolf
 */
public class AttributeConstructor extends NodeConstructor {

	final String qname;
	public final List<Object> contents = new ArrayList<Object>(5);
	boolean isNamespaceDecl = false;
	
	public AttributeConstructor(XQueryContext context, String name) {
		super(context);
		if(name.startsWith("xmlns"))
			{isNamespaceDecl = true;}
		this.qname = name;
	}
	
	public void addValue(String value) {
		contents.add(value);
	}
	
	public void addEnclosedExpr(Expression expr) throws XPathException {
		if(isNamespaceDecl)
			{throw new XPathException(this, "enclosed expressions are not allowed in namespace " +
				"declaration attributes");}
		contents.add(expr);
	}
	
	public String getQName() {
		return qname;
	}
	
	public boolean isNamespaceDeclaration() {
		return isNamespaceDecl;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        contextInfo.setParent(this);
        for(final Object next : contents) {
			if(next instanceof Expression)
				{((Expression)next).analyze(contextInfo);}
		}
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		final StringBuilder buf = new StringBuilder();

		for(final Object next : contents) {
			if(next instanceof Expression)
				{evalEnclosedExpr(((Expression)next).eval(contextSequence, contextItem), buf);}
			else
				{buf.append(next);}
		}
		//TODO : include that tricky attribute normalization here
		final StringValue result = new StringValue(buf.toString());
        // String values as expressions are already expanded by
        // the parser -- Alex
		//result.expand();
		return result;
	}

	private void evalEnclosedExpr(Sequence seq, StringBuilder buf) throws XPathException {
		Item item;
		AtomicValue atomic;
		for(final SequenceIterator i = seq.iterate(); i.hasNext();) {
			item = i.nextItem();
			atomic = item.atomize();
			buf.append(atomic.getStringValue());
			if(i.hasNext())
				{buf.append(' ');}
		}
	}
	
	/**
	 * If this is a namespace declaration attribute, return
	 * the single string value of the attribute.
	 */
	public String getLiteralValue() {
		if(contents.size() == 0)
			{return "";}
		return (String)contents.get(0);
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("attribute ");
        //TODO : remove curly braces if Qname
        dumper.display("{");
        dumper.display(qname);
        dumper.display("} ");
        dumper.display("{");
        dumper.startIndent();

		for(final Object next : contents) {
			if(next instanceof Expression)
				{((Expression)next).dump(dumper);}
			else
				{dumper.display(next);}
		}
        dumper.endIndent();
        dumper.nl().display("} ");
    }
    
    public String toString() {
    	final StringBuilder result = new StringBuilder();
    	result.append("attribute ");
        //TODO : remove curly braces if Qname
        result.append("{"); 
        result.append(qname);
        result.append("} "); 
        result.append("{");        

		for(final Object next : contents) {
			if(next instanceof Expression)
				{result.append(next.toString());}
			else
				{result.append(next.toString());}
		}      
		result.append("} ");
		return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.NodeConstructor#resetState()
	 */
    @Override
    public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);

		for(final Object object : contents) {
			if(object instanceof Expression)
				{((Expression)object).resetState(postOptimization);}
		}
	}

    public Iterator<Object> contentIterator() {
        return contents.iterator();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitAttribConstructor(this);
    }
}
