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
package org.exist.xquery.functions.text;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FuzzyMatchAny extends FuzzyMatchAll {

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("fuzzy-match-any", TextModule.NAMESPACE_URI, TextModule.PREFIX),
			"Fuzzy keyword search, which compares strings based on the Levenshtein distance " +
			"(or edit distance). The function tries to match any of the keywords specified in the " +
			"keyword string against the string value of each item in the sequence $source.",
			new SequenceType[]{
				new FunctionParameterSequenceType("source", Type.NODE, Cardinality.ZERO_OR_MORE, "The source"),
				new FunctionParameterSequenceType("keyword", Type.STRING, Cardinality.EXACTLY_ONE, "The keyword string")},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "the sequence of nodes that match the keywords"),
			true);
	
	/**
	 * @param context
	 */
	public FuzzyMatchAny(XQueryContext context) {
		super(context, Constants.FULLTEXT_OR, signature);
	}
}
