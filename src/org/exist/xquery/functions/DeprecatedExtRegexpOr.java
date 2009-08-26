/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class DeprecatedExtRegexpOr extends DeprecatedExtRegexp {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("match-any", Function.BUILTIN_FUNCTION_NS),
				"Tries to match each of the regular expression " +
				"strings passed in $regular-expression and all following parameters against the keywords contained in " +
				"the old fulltext index. The keywords found are then compared to the node set in $nodes. Every " +
				"node containing any of the keywords is copied to the result sequence.",
            new SequenceType[] { SOURCE_PARAM, REGEX_PARAM },
			RETURN_TYPE,
			true,
            "This function is eXist-specific and should not be in the standard functions namespace. Please " +
            "use text:match-any() instead.");

	/**
	 * 
	 */
	public DeprecatedExtRegexpOr(XQueryContext context) {
		super(context, Constants.FULLTEXT_OR);
	}

}
