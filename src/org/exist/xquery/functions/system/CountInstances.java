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
 *  $Id:
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Return details about eXist instances
 * 
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class CountInstances extends BasicFunction
{
    protected final static Logger logger = LogManager.getLogger(CountInstances.class);

	public final static FunctionSignature countInstancesMax =
		new FunctionSignature(
			new QName("count-instances-max", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the maximum number of eXist instances.",
			FunctionSignature.NO_ARGS,
			new FunctionParameterSequenceType("result", Type.INTEGER, Cardinality.EXACTLY_ONE, "the count")
		);
	
	public final static FunctionSignature countInstancesActive =
		new FunctionSignature(
			new QName("count-instances-active", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the number of eXist instances that are active.",
			FunctionSignature.NO_ARGS,
			new FunctionParameterSequenceType("result", Type.INTEGER, Cardinality.EXACTLY_ONE, "the count")
		);
	
	public final static FunctionSignature countInstancesAvailable =
		new FunctionSignature(
				new QName("count-instances-available", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
				"Returns the number of eXist instances that are available.",
				FunctionSignature.NO_ARGS,
				new FunctionParameterSequenceType("result", Type.INTEGER, Cardinality.EXACTLY_ONE, "the count")
		);
	
	private BrokerPool bp = null;
	

	public CountInstances(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
		
		bp = context.getBroker().getBrokerPool();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		int count = 0;
		
		if(isCalledAs("count-instances-max"))
		{
			count = bp.getMax();
		}
		else if(isCalledAs("count-instances-active"))
		{
			count = bp.countActiveBrokers();
		}
		else if(isCalledAs("count-instances-available"))
		{
			count = bp.available();
		}
		
		return new IntegerValue(count, Type.INTEGER);
	}
}
