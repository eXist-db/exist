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
package org.exist.xpath.functions.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Vector;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.XPathUtil;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.JavaObjectValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class Invoke extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("invoke", UTIL_FUNCTION_NS, "util"),
			new SequenceType[] {
				new SequenceType(Type.JAVA_OBJECT, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
			true);

	public Invoke(StaticContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Function#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(DocumentSet docs, Sequence contextSequence, Item contextItem) throws XPathException {
		JavaObjectValue value = (JavaObjectValue)
			getArgument(0).eval(docs, contextSequence, contextItem).itemAt(0);
		String methodName = getArgument(1).eval(docs, contextSequence, contextItem).getStringValue();
		
		Object obj = value.getObject();
		Class clazz = obj.getClass();
		Method methods[] = clazz.getMethods();
		Method method;
		for(int i = 0; i < methods.length; i++) {
			method = methods[i];
			if(method.getName().equals(methodName) && Modifier.isPublic(method.getModifiers())) {
				Class parameters[] = method.getParameterTypes();
				if(parameters.length == getArgumentCount() - 2) {
					Object[] args = getArgs(docs, contextSequence, contextItem, parameters);
				}
			}
		}
		return null;
	}

	private Object[] getArgs(DocumentSet docs, Sequence contextSequence, Item contextItem,
		Class parameters[]) throws XPathException {
		Object[] args = new Object[parameters.length];
		Class param;
		int type;
		Sequence seq;
		for(int i = 0; i < args.length; i++) {
			param = parameters[i];
			type = XPathUtil.javaClassToXPath(param);
			seq = getArgument(i + 2).eval(docs, contextSequence, contextItem);
		}
		return null;
	}
}
