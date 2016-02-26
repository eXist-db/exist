/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.Namespaces;
import org.exist.dom.QName;

/**
 * Represents an XQuery option declared via "declare option".
 * 
 * @author wolf
 */
public class Option {

	public final static QName TIMEOUT_QNAME = new QName("timeout", Namespaces.EXIST_NS);
	public final static QName OUTPUT_SIZE_QNAME = new QName("output-size-limit", Namespaces.EXIST_NS);
	public final static QName SERIALIZE_QNAME = new QName("serialize", Namespaces.EXIST_NS);
    public final static QName PROFILE_QNAME = new QName("profiling", Namespaces.EXIST_NS);
    public final static QName OPTIMIZE_QNAME = new QName("optimize", Namespaces.EXIST_NS);
    public final static QName OPTIMIZE_IMPLICIT_TIMEZONE = new QName("implicit-timezone", Namespaces.EXIST_NS);
    public final static QName CURRENT_DATETIME = new QName("current-dateTime", Namespaces.EXIST_NS);
	
    private final static String[] EMPTY = new String[0];

    private final static String paramPattern =
		"\\s*([\\w\\.-]+)\\s*=\\s*('[^']*'|\"[^\"]*\"|[^\"\'\\s][^\\s]*)";
	
	private final static Pattern pattern = Pattern.compile(paramPattern);
    
	private final QName qname;
	private final String contents;
	
	public Option(QName qname, String contents)  throws XPathException {
		if (qname.getPrefix() == null || "".equals(qname.getPrefix()))
			{throw new XPathException("XPST0081: options must have a prefix");} 
		this.qname = qname;
		this.contents = contents;
	}
	
	public QName getQName() {
		return qname;
	}
	
	public String getContents() {
		return contents;
	}
	
	public String[] tokenizeContents() {
		return tokenize(contents);
	}


    public static String[] tokenize(final String contents) {
        if(contents == null) {
            return EMPTY;
        }
		final StringTokenizer tok = new StringTokenizer(contents, " \r\t\n");
		final String[] items = new String[tok.countTokens()];
		for(int i = 0; tok.hasMoreTokens(); i++) {
			items[i] = tok.nextToken();
		}
		return items;
    }

    public static String[] parseKeyValuePair(final String s) {
        final Matcher matcher = pattern.matcher(s);
		if(matcher.matches()) {
			String value = matcher.group(2);
			if(value.charAt(0) == '\'' || value.charAt(0) == '"') {
				value = value.substring(1, value.length() - 1);
			}
			return new String[] { matcher.group(1), value };
		}
		return null;
	}

	@Override
	public boolean equals(final Object other) {
		return other != null && 
            other instanceof Option &&
            qname.equals(((Option) other).qname);
	}
}
