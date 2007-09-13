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
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class ExtRegexpOr extends ExtRegexp {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("match-any", Function.BUILTIN_FUNCTION_NS),
				"eXist-specific extension function. Tries to match each of the regular expression " +
				"strings passed in $b and all following parameters against the keywords contained in " +
				"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
				"node containing any of the keywords is copied to the result sequence.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true,
            "This function is eXist-specific and should not be in the standard functions namespace. Please " +
            "use text:match-any() instead.");

	/**
	 * 
	 */
	public ExtRegexpOr(XQueryContext context) {
		super(context, Constants.FULLTEXT_OR);
	}

}
