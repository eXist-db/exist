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
 *  $Id: ExistVersion.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.system;

import java.io.IOException;
import java.util.Properties;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.util.UtilModule;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Return the eXist version
 * 
 * @author wolf
 */
public class GetVersion extends BasicFunction
{

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-version", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the version of eXist running this query.",
			FunctionSignature.NO_ARGS,
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("eXist-version", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the version of eXist running this query.",
			FunctionSignature.NO_ARGS,
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			"Moved to system module and renamed to system:get-version.");

	public GetVersion(XQueryContext context)
	{
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		Properties sysProperties = new Properties();
		try
		{
			sysProperties.load(GetVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		}
		catch (IOException e)
		{
			LOG.debug("Unable to load system.properties from class loader");
		}
		return new StringValue(sysProperties.getProperty("product-version", "unknown version"));
	}
}
