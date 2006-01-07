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

import java.io.IOException;
import java.util.Properties;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.StringValue;

/**
 * Return the eXist version
 * 
 * @author wolf
 */
public class ExistVersion extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("eXist-version", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the version of eXist running this query.",
			FunctionSignature.NO_ARGS,
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));

	public ExistVersion(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
			Sequence contextSequence,
			Item contextItem)
	{
		Properties sysProperties = new Properties();
		try {
			sysProperties.load(ExistVersion.class.getClassLoader().getResourceAsStream("org/exist/system.properties"));
		} catch (IOException e) {
			LOG.debug("Unable to load system.properties from class loader");
		}
		return new StringValue(sysProperties.getProperty("product-version", "unknown version"));
	}

}
