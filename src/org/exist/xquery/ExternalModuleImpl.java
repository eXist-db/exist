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

import java.util.Iterator;
import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

/**
 * Default implementation of an {@link org.exist.xpath.ExternalModule}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ExternalModuleImpl implements ExternalModule {

	private String mNamespaceURI;
	private String mPrefix;

	private TreeMap mFunctionMap = new TreeMap();

	private TreeMap mGlobalVariables = new TreeMap();
	private TreeMap mStaticVariables = new TreeMap();

	public ExternalModuleImpl(String namespaceURI, String prefix) {
		mNamespaceURI = namespaceURI;
		mPrefix = prefix;
	}

	public UserDefinedFunction getFunction(QName qname) {
		return (UserDefinedFunction) mFunctionMap.get(qname);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.ExternalModule#declareFunction(org.exist.xpath.UserDefinedFunction)
	 */
	public void declareFunction(UserDefinedFunction func) {
		mFunctionMap.put(func.getSignature().getName(), func);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return mNamespaceURI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return mPrefix;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#isInternalModule()
	 */
	public boolean isInternalModule() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#listFunctions()
	 */
	public FunctionSignature[] listFunctions() {
		FunctionSignature signatures[] = new FunctionSignature[mFunctionMap.size()];
		int j = 0;
		for (Iterator i = mFunctionMap.values().iterator(); i.hasNext(); j++) {
			signatures[j] = ((UserDefinedFunction) i.next()).getSignature();
		}
		return signatures;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#getSignatureForFunction(org.exist.dom.QName)
	 */
	public FunctionSignature getSignatureForFunction(QName qname) {
		UserDefinedFunction func = (UserDefinedFunction) mFunctionMap.get(qname);
		if (func != null)
			return func.getSignature();
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#declareVariable(org.exist.dom.QName, java.lang.Object)
	 */
	public Variable declareVariable(QName qname, Object value) throws XPathException {
		Sequence val = XPathUtil.javaObjectToXPath(value);
		Variable var = (Variable) mStaticVariables.get(qname);
		if (var == null) {
			var = new Variable(qname);
			mStaticVariables.put(qname, var);
		}
		var.setValue(val);
		return var;
	}

	public void declareVariable(QName qname, VariableDeclaration decl) throws XPathException {
		mGlobalVariables.put(qname, decl);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Module#resolveVariable(org.exist.dom.QName)
	 */
	public Variable resolveVariable(QName qname) throws XPathException {
		VariableDeclaration decl = (VariableDeclaration)mGlobalVariables.get(qname);
		if(decl != null) {
			decl.eval(null);
		}
		return (Variable) mStaticVariables.get(qname);
	}
}
