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
package org.exist.xpath;

/**
 * Specifies the class names of all built-in functions.
 * 
 * Class {@link org.exist.xpath.StaticContext} will introspect the classes listed here
 * and store them into the list of in-scope functions.
 * 
 * @author wolf
 */
public final class SystemFunctions {

	public final static String[] internalFunctions =
	{ 
		"org.exist.xpath.functions.FunMatches",
		"org.exist.xpath.functions.FunReplace",
		"org.exist.xpath.functions.FunTokenize",
		"org.exist.xpath.functions.FunSubstring",
		"org.exist.xpath.functions.FunSubstringBefore",
		"org.exist.xpath.functions.FunSubstringAfter",
		"org.exist.xpath.functions.FunNormalizeSpace",
		"org.exist.xpath.functions.FunStringPad",
		"org.exist.xpath.functions.FunConcat",
		"org.exist.xpath.functions.FunStartsWith",
		"org.exist.xpath.functions.FunEndsWith",
		"org.exist.xpath.functions.FunContains",
		"org.exist.xpath.functions.FunNot",
		"org.exist.xpath.functions.FunPosition",
		"org.exist.xpath.functions.FunLast",
		"org.exist.xpath.functions.FunCount",
		"org.exist.xpath.functions.FunStrLength",
		"org.exist.xpath.functions.FunBoolean",
		"org.exist.xpath.functions.FunString",
		"org.exist.xpath.functions.FunNumber",
		"org.exist.xpath.functions.FunTrue",
		"org.exist.xpath.functions.FunFalse",
		"org.exist.xpath.functions.FunSum",
		"org.exist.xpath.functions.FunFloor",
		"org.exist.xpath.functions.FunCeiling",
		"org.exist.xpath.functions.FunRound",
		"org.exist.xpath.functions.FunName",
		"org.exist.xpath.functions.FunLocalName",
		"org.exist.xpath.functions.FunNamespaceURI",
		"org.exist.xpath.functions.FunId",
		"org.exist.xpath.functions.FunLang",
		"org.exist.xpath.functions.FunBaseURI",
		"org.exist.xpath.functions.FunDocumentURI",
		"org.exist.xpath.functions.ExtRegexp",
		"org.exist.xpath.functions.ExtRegexpOr",
		"org.exist.xpath.functions.ExtDocument",
		"org.exist.xpath.functions.ExtCollection",
		"org.exist.xpath.functions.ExtXCollection",
		"org.exist.xpath.functions.ExtDoctype",
		"org.exist.xpath.functions.FunDistinctValues",
		"org.exist.xpath.functions.FunEmpty",
		"org.exist.xpath.functions.FunExists",
		"org.exist.xpath.functions.FunSubSequence",
		"org.exist.xpath.functions.FunItemAt",
		"org.exist.xpath.functions.FunZeroOrOne",
		"org.exist.xpath.functions.FunOneOrMore",
		"org.exist.xpath.functions.FunExactlyOne",
		"org.exist.xpath.functions.xmldb.XMLDBCollection",
		"org.exist.xpath.functions.xmldb.XMLDBStore",
		"org.exist.xpath.functions.xmldb.XMLDBRegisterDatabase",
		"org.exist.xpath.functions.xmldb.XMLDBCreateCollection",
		"org.exist.xpath.functions.util.MD5",
		"org.exist.xpath.functions.util.DescribeFunction",
		"org.exist.xpath.functions.util.BuiltinFunctions",
		"org.exist.xpath.functions.util.EvalFunction",
		"org.exist.xpath.functions.request.RequestParameter",
		"org.exist.xpath.functions.request.GetSessionAttribute",
		"org.exist.xpath.functions.request.SetSessionAttribute"
	};

}
