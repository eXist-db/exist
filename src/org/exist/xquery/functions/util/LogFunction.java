/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class LogFunction extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("log", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Logs the message specified in $b to the current logger. $a indicates " +
			"the log priority, e.g. 'debug' or 'warn'.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("log-system-out", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Logs the message specified in $b to System.out.",
			new SequenceType[] {
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		),
		new FunctionSignature(
			new QName("log-system-err", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Logs the message specified in $b to System.err.",
			new SequenceType[] {
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)
		)
	};
	
	public LogFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		SequenceIterator i;
		if (isCalledAs("log")) {
			i = args[1].unorderedIterator();
			if(args[1].isEmpty())
				return Sequence.EMPTY_SEQUENCE;
		} else {
			i = args[0].unorderedIterator();
			if(args[0].isEmpty())
				return Sequence.EMPTY_SEQUENCE;
		}
			
		// add line of the log statement
		StringBuffer buf = new StringBuffer();
		buf.append("(Line: ");
		buf.append(getASTNode().getLine());
		buf.append(") ");
		
		while(i.hasNext()) {
			Item next = i.nextItem();
			if (Type.subTypeOf(next.getType(), Type.NODE)) {
				Serializer serializer = context.getBroker().getSerializer();
				serializer.reset();
				try {
					buf.append(serializer.serialize((NodeValue) next));
				} catch (SAXException e) {
					throw new XPathException(getASTNode(), "An exception occurred while serializing node to log: " +
							e.getMessage(), e);
				}
			} else
				buf.append(next.getStringValue());
		}
                
		if (isCalledAs("log")) {
			String priority = args[0].getStringValue();			
			if(priority.equalsIgnoreCase("error"))
				LOG.error(buf);
			else if(priority.equalsIgnoreCase("warn"))
				LOG.warn(buf);
			else if(priority.equalsIgnoreCase("info"))
				LOG.info(buf);
			else if(priority.equalsIgnoreCase("trace"))
				LOG.trace(buf);
			else
				LOG.debug(buf);
		} else if (isCalledAs("log-system-out")) {
			System.out.println(buf);
		} else if (isCalledAs("log-system-err")) {
			System.err.println(buf);				
		}
                
		return Sequence.EMPTY_SEQUENCE;
	}
}
