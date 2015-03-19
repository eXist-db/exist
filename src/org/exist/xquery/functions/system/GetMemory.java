/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Return details abount memory use for eXist
 * 
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class GetMemory extends BasicFunction
{
    protected final static Logger logger = LogManager.getLogger(GetMemory.class);

	public final static FunctionSignature getMemoryMax =
		new FunctionSignature(
			new QName("get-memory-max", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the maximum amount of memory eXist may use.",
			FunctionSignature.NO_ARGS,
			new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "the size of memory")
		);
	
	public final static FunctionSignature getMemoryTotal =
		new FunctionSignature(
			new QName("get-memory-total", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the total amount of memory in use by eXist.",
			FunctionSignature.NO_ARGS,
			new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "the size of memory")
		);
	
	public final static FunctionSignature getMemoryFree =
		new FunctionSignature(
				new QName("get-memory-free", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
				"Returns the amount of free memory available to eXist.",
				FunctionSignature.NO_ARGS,
				new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "the size of memory")
		);

	public GetMemory(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		final Runtime rt = Runtime.getRuntime();
		
		long memory = 0;
		
		if(isCalledAs("get-memory-max"))
		{
			memory = rt.maxMemory();
		}
		else if(isCalledAs("get-memory-total"))
		{
			memory = rt.totalMemory();
		}
		else if(isCalledAs("get-memory-free"))
		{
			memory = rt.freeMemory();
		}
		
		return new IntegerValue(memory, Type.LONG);
	}
}
