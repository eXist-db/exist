/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
package org.exist.dom;

import org.exist.xpath.StaticContext;

public class QName implements Comparable {
	
	public final static QName TEXT_QNAME = new QName("#text", "", null);
	public final static QName COMMENT_QNAME = new QName("#comment", "", null);
	public final static QName DOCTYPE_QNAME = new QName("#doctype", "", null);
	
	private String localName_ = null;
	private String namespaceURI_ = null;
	private String prefix_ = null;
	
	public QName(String localName, String namespaceURI, String prefix) {
		localName_ = localName;
		namespaceURI_ = namespaceURI;
		prefix_ = prefix;
	}
	
	public String getLocalName() {
		return localName_;
	}
	
	public void setLocalName(String name) {
		localName_ = name;
	}
	
	public String getNamespaceURI() {
		return namespaceURI_;
	}
	
	public void setNamespaceURI(String namespaceURI) {
		namespaceURI_ = namespaceURI;
	}
	
	public boolean needsNamespaceDecl() {
		return namespaceURI_ != null && namespaceURI_.length() > 0;
	}
	
	public String getPrefix() {
		return prefix_;
	}
	
	public void setPrefix(String prefix) {
		prefix_ = prefix;
	}
	
	public String toString() {
		if(prefix_ != null && prefix_.length() > 0)
			return prefix_ + ':' + localName_;
		else
			return localName_;
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		QName other = (QName)o;
		int c;
		if(namespaceURI_ == null)
			c = other.namespaceURI_ == null ? 0 : -1;
		else if(other.namespaceURI_ == null)
			c = 1;
		else
			c = namespaceURI_.compareTo(other.namespaceURI_);
		return c == 0 ? localName_.compareTo(other.localName_) : c;
	}
	
	public static String extractPrefix(String qname) {
		int p = qname.indexOf(':');
		if(p < 0)
			return null;
		if(p == 0)
			throw new IllegalArgumentException("Illegal QName: starts with a :");
		return qname.substring(0, p);
	}
	
	public static String extractLocalName(String qname) {
		int p = qname.indexOf(':');
		if(p < 0)
			return qname;
		if(p == 0)
			throw new IllegalArgumentException("Illegal QName: starts with a :");
		if(p == qname.length())
			throw new IllegalArgumentException("Illegal QName: ends with a :");
		return qname.substring(p + 1);
	}
	
	public static QName parse(StaticContext context, String qname) {
		String prefix = extractPrefix(qname);
		String namespaceURI;
		if(prefix != null) {
			namespaceURI = context.getURIForPrefix(prefix);
			if(namespaceURI == null)
				throw new IllegalArgumentException("No namespace defined for prefix " + prefix);
		} else
			namespaceURI = context.getURIForPrefix("");
		if(namespaceURI == null)
			namespaceURI = "";
		return new QName(extractLocalName(qname), namespaceURI, prefix);
	}
}
