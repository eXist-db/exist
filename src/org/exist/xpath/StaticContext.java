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

import java.util.HashMap;
import java.util.Iterator;

import org.exist.dom.SymbolTable;
import org.exist.security.User;
import org.exist.storage.DBBroker;

public class StaticContext {

	protected static final String[][] internalFunctions =
		{
			{ "substring", "org.exist.xpath.FunSubstring" },
			{ "substring-before", "org.exist.xpath.FunSubstringBefore" },
			{ "substring-after", "org.exist.xpath.FunSubstringAfter" },
			{ "normalize-space", "org.exist.xpath.FunNormalizeString" },
			{ "concat", "org.exist.xpath.FunConcat" },
			{ "starts-with", "org.exist.xpath.FunStartsWith" },
			{ "ends-with", "org.exist.xpath.FunEndsWith" },
			{ "contains", "org.exist.xpath.FunContains" },
			{ "not", "org.exist.xpath.FunNot" }, 
			{ "position", "org.exist.xpath.FunPosition" },
			{ "last", "org.exist.xpath.FunLast" }, 
			{ "count", "org.exist.xpath.FunCount" }, 
			{ "string-length", "org.exist.xpath.FunStrLength" }, 
			{ "boolean", "org.exist.xpath.FunBoolean" }, 
			{ "string", "org.exist.xpath.FunString" }, 
			{ "number", "org.exist.xpath.FunNumber" }, 
			{ "true", "org.exist.xpath.FunTrue" }, 
			{ "false", "org.exist.xpath.FunFalse" }, 
			{ "sum", "org.exist.xpath.FunSum" }, 
			{ "floor", "org.exist.xpath.FunFloor" }, 
			{ "ceiling", "org.exist.xpath.FunCeiling" }, 
			{ "round", "org.exist.xpath.FunRound" }, 
			{ "name", "org.exist.xpath.FunName" }, 
			{ "local-name", "org.exist.xpath.FunLocalName" },
			{ "namespace-uri", "org.exist.xpath.FunNamespaceURI" },
			{ "match-any", "org.exist.xpath.FunKeywordMatchAny" }, 
			{ "match-all", "org.exist.xpath.FunKeywordMatchAll" }, 
			{ "id", "org.exist.xpath.FunId" },
			{ "lang", "org.exist.xpath.FunLang" }
	};

	private HashMap namespaces;
	private HashMap functions;
	private DBBroker broker;
	
	public StaticContext(DBBroker broker) {
		this.broker = broker;
		loadDefaults();
	}

	public void declareNamespace(String prefix, String uri) {
		if (prefix == null || uri == null)
			throw new IllegalArgumentException("null argument passed to declareNamespace");
		namespaces.put(prefix, uri);
	}

	public String getURIForPrefix(String prefix) {
		return (String) namespaces.get(prefix);
	}

	public void removeNamespace(String uri) {
		for (Iterator i = namespaces.values().iterator(); i.hasNext();) {
			if (((String) i.next()).equals(uri)) {
				i.remove();
			}
		}
	}

	public void clearNamespaces() {
		namespaces.clear();
		loadDefaults();
	}

	public String getClassForFunction(String fnName) {
		return (String)functions.get(fnName);
	}
	
	public void setBroker(DBBroker broker) {
		this.broker = broker;
	}
	
	public DBBroker getBroker() {
		return broker;
	}
	
	public User getUser() {
		return broker.getUser();
	}
	
	private void loadDefaults() {
		SymbolTable syms = DBBroker.getSymbols();
		String[] prefixes = syms.defaultPrefixList();
		namespaces = new HashMap(prefixes.length);
		for (int i = 0; i < prefixes.length; i++) {
			namespaces.put(prefixes[i], syms.getDefaultNamespace(prefixes[i]));
		}
			
		functions = new HashMap(internalFunctions.length);
		for (int i = 0; i < internalFunctions.length; i++)
			functions.put(internalFunctions[i][0], internalFunctions[i][1]);
	}
}
