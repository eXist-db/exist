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

import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

/**
 * Abstract base class for an {@link org.exist.xquery.InternalModule}. 
 * The constructor expects an array of {@link org.exist.xquery.FunctionDef}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public abstract class AbstractInternalModule implements InternalModule {

	protected TreeMap mFunctionMap = new TreeMap();
	protected FunctionDef[] mFunctions;
	
	protected TreeMap mGlobalVariables = new TreeMap();
	
	public AbstractInternalModule(FunctionDef[] functions) {
		mFunctions = functions;
		for(int i = 0; i < functions.length; i++) {
			FunctionSignature signature = functions[i].getSignature();
			mFunctionMap.put(signature.getName(), functions[i]);
		}
	}

	private AbstractInternalModule() {
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#isInternalModule()
	 */
	public boolean isInternalModule() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public abstract String getNamespaceURI();

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public abstract String getDefaultPrefix();

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#listFunctions()
	 */
	public FunctionSignature[] listFunctions() {
		FunctionSignature signatures[] = new FunctionSignature[mFunctions.length];
		for(int i = 0; i < signatures.length; i++)
			signatures[i] = mFunctions[i].getSignature();
		return signatures;
	}

	public FunctionSignature getSignatureForFunction(QName qname) {
		FunctionDef def = (FunctionDef)mFunctionMap.get(qname);
		if(def != null)
			return def.getSignature();
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getClassForFunction(org.exist.dom.QName)
	 */
	public Class getClassForFunction(QName qname) {
		FunctionDef def = (FunctionDef)mFunctionMap.get(qname);
		if(def != null)
			return def.getImplementingClass();
		return null;
	}
	
	public Variable declareVariable(QName qname, Object value) throws XPathException {
		Sequence val = XPathUtil.javaObjectToXPath(value);
		Variable var = (Variable)mGlobalVariables.get(qname);
		if(var == null) {
			var = new Variable(qname);
			mGlobalVariables.put(qname, var);
		}
		var.setValue(val);
		return var;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#resolveVariable(org.exist.dom.QName)
	 */
	public Variable resolveVariable(QName qname) throws XPathException {
		return (Variable)mGlobalVariables.get(qname);
	}
}
