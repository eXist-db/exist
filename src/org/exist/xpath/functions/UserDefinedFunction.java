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
package org.exist.xpath.functions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.*;
import org.exist.xpath.Dependency;
import org.exist.xpath.Expression;
import org.exist.xpath.StaticContext;
import org.exist.xpath.Variable;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

/**
 * @author wolf
 */
public class UserDefinedFunction extends Function {

	private Expression body;
	
	private List parameters = new ArrayList(3);
	
	private Sequence[] currentArguments = null;
	
	public UserDefinedFunction(StaticContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public void setFunctionBody(Expression body) {
		this.body = body;
	}
	
	public void addVariable(String varName) throws XPathException {
		QName qname = QName.parse(context, varName);
		parameters.add(qname);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Function#setArguments(java.util.List)
	 */
	public void setArguments(Sequence[] args) throws XPathException {
		this.currentArguments = args;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		QName varName;
		Variable var;
		Sequence argSeq;
		int j = 0;
		for(Iterator i = parameters.iterator(); i.hasNext(); j++) {
			varName = (QName)i.next();
			var = new Variable(varName);
			var.setValue(currentArguments[j]);
			context.declareVariable(var);
		}
		return body.eval(docs, contextSequence, contextItem);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#pprint()
	 */
	public String pprint() {
		FunctionSignature signature = getSignature();
		StringBuffer buf = new StringBuffer();
		buf.append("declare function ");
		buf.append(signature.getName());
		buf.append('(');
		for(int i = 0; i < signature.getArgumentTypes().length; i++) {
			if(i > 0)
				buf.append(", ");
			buf.append(signature.getArgumentTypes()[i]);
		}
		buf.append(')');
		return buf.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM
			+ Dependency.CONTEXT_POSITION;
	}
}
