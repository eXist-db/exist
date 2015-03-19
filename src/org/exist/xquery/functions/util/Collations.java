/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2004-09 Wolfgang M. Meier
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

import java.text.Collator;
import java.util.Locale;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Library function to return all collation locales currently known to the system.
 * 
 * @author wolf
 */
public class Collations extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(Collations.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("collations", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence of strings containing all collation locales that might be " +
			"specified in the '?lang=' parameter of a collation URI.",
			FunctionSignature.NO_ARGS,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the sequence of strings containing all collation locales that might be " +
			"specified in the '?lang=' parameter of a collation URI."));
	
	/**
	 * @param context
	 */
	public Collations(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		final ValueSequence result = new ValueSequence();
		final Locale[] locales = Collator.getAvailableLocales();
		String locale;
		for(int i = 0; i < locales.length; i++) {
			locale = locales[i].getLanguage();
			if(locales[i].getCountry().length() > 0)
				{locale += '-' + locales[i].getCountry();}
			result.add(new StringValue(locale));
		}
		return result;
	}

}
