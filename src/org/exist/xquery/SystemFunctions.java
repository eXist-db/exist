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
package org.exist.xquery;

/**
 * Specifies the class names of all built-in functions.
 * 
 * Class {@link org.exist.xquery.StaticContext} will introspect the classes listed here
 * and store them into the list of in-scope functions.
 * 
 * @author wolf
 */
public final class SystemFunctions {

	public final static String[] internalFunctions =
	{ 
		"org.exist.xquery.functions.FunMatches",
		"org.exist.xquery.functions.FunReplace",
		"org.exist.xquery.functions.FunTokenize",
		"org.exist.xquery.functions.FunSubstring",
		"org.exist.xquery.functions.FunSubstringBefore",
		"org.exist.xquery.functions.FunSubstringAfter",
		"org.exist.xquery.functions.FunNormalizeSpace",
		"org.exist.xquery.functions.FunStringPad",
		"org.exist.xquery.functions.FunConcat",
		"org.exist.xquery.functions.FunStartsWith",
		"org.exist.xquery.functions.FunEndsWith",
		"org.exist.xquery.functions.FunContains",
		"org.exist.xquery.functions.FunNot",
		"org.exist.xquery.functions.FunPosition",
		"org.exist.xquery.functions.FunLast",
		"org.exist.xquery.functions.FunCount",
		"org.exist.xquery.functions.FunStrLength",
		"org.exist.xquery.functions.FunBoolean",
		"org.exist.xquery.functions.FunString",
		"org.exist.xquery.functions.FunNumber",
		"org.exist.xquery.functions.FunTrue",
		"org.exist.xquery.functions.FunFalse",
		"org.exist.xquery.functions.FunSum",
		"org.exist.xquery.functions.FunFloor",
		"org.exist.xquery.functions.FunCeiling",
		"org.exist.xquery.functions.FunRound",
		"org.exist.xquery.functions.FunName",
		"org.exist.xquery.functions.FunLocalName",
		"org.exist.xquery.functions.FunNamespaceURI",
		"org.exist.xquery.functions.FunId",
		"org.exist.xquery.functions.FunLang",
		"org.exist.xquery.functions.FunBaseURI",
		"org.exist.xquery.functions.FunDocumentURI",
		"org.exist.xquery.functions.ExtRegexp",
		"org.exist.xquery.functions.ExtRegexpOr",
		"org.exist.xquery.functions.ExtDocument",
		"org.exist.xquery.functions.ExtCollection",
		"org.exist.xquery.functions.ExtXCollection",
		"org.exist.xquery.functions.ExtDoctype",
		"org.exist.xquery.functions.FunDistinctValues",
		"org.exist.xquery.functions.FunEmpty",
		"org.exist.xquery.functions.FunExists",
		"org.exist.xquery.functions.FunSubSequence",
		"org.exist.xquery.functions.FunItemAt",
		"org.exist.xquery.functions.FunZeroOrOne",
		"org.exist.xquery.functions.FunOneOrMore",
		"org.exist.xquery.functions.FunExactlyOne",
		"org.exist.xquery.functions.FunDoc",
		"org.exist.xquery.functions.FunAbs",
		"org.exist.xquery.functions.FunMax",
		"org.exist.xquery.functions.FunMin",
		"org.exist.xquery.functions.FunAvg",
		"org.exist.xquery.functions.FunUpperCase",
		"org.exist.xquery.functions.FunLowerCase",
		"org.exist.xquery.functions.FunTranslate",
		"org.exist.xquery.functions.FunRoot",
		"org.exist.xquery.functions.FunCurrentDateTime",
		"org.exist.xquery.functions.FunCurrentDate",
		"org.exist.xquery.functions.FunCurrentTime",
		"org.exist.xquery.functions.FunGetSecondsFromDayTimeDuration",
		"org.exist.xquery.functions.FunGetMonthFromDate",
		"org.exist.xquery.functions.FunGetYearFromDate",
		"org.exist.xquery.functions.xmldb.XMLDBCollection",
		"org.exist.xquery.functions.xmldb.XMLDBStore",
		"org.exist.xquery.functions.xmldb.XMLDBRegisterDatabase",
		"org.exist.xquery.functions.xmldb.XMLDBCreateCollection",
		"org.exist.xquery.functions.util.MD5",
		"org.exist.xquery.functions.util.DescribeFunction",
		"org.exist.xquery.functions.util.BuiltinFunctions",
		"org.exist.xquery.functions.util.EvalFunction",
		"org.exist.xquery.functions.request.RequestParameter",
		"org.exist.xquery.functions.request.GetSessionAttribute",
		"org.exist.xquery.functions.request.SetSessionAttribute"
	};

}
