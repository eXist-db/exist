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
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class LogFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("log", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
			"Logs the message specified in $b to the current logger. $a indicates " +
			"the log priority, e.g. 'debug' or 'warn'.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY));
	
	public LogFunction(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if(args[1].getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		String priority = args[0].getStringValue();
		CharSequence message;
		if(args[1].getLength() == 1)
			message = args[1].getStringValue();
		else {
			StringBuffer buf = new StringBuffer();
			for(SequenceIterator i = args[1].unorderedIterator(); i.hasNext(); ) {
				buf.append(i.nextItem().getStringValue());
			}
			message = buf;
		}
		if(priority.equalsIgnoreCase("error"))
			LOG.error(message);
		else if(priority.equalsIgnoreCase("warn"))
			LOG.warn(message);
		else if(priority.equalsIgnoreCase("info"))
			LOG.info(message);
		else
			LOG.debug(message);
		return Sequence.EMPTY_SEQUENCE;
	}
}
