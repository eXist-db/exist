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
package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.value.Sequence;

/**
 * Abstract base class for an {@link org.exist.xquery.InternalModule}. 
 * Functions are defined in an array of {@link org.exist.xquery.FunctionDef}, which
 * is passed to the constructor. A single implementation class
 * can be registered for more than one function signature, given that the signatures differ
 * in name or the number of expected arguments. It is thus possible to implement
 * similar XQuery functions in one single class.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public abstract class AbstractInternalModule implements InternalModule {

	private final static Logger LOG = Logger.getLogger(AbstractInternalModule.class);
	
	protected final TreeMap mFunctionMap = new TreeMap();
	protected FunctionDef[] mFunctions;
	
	protected final TreeMap mGlobalVariables = new TreeMap();
	
	public AbstractInternalModule(FunctionDef[] functions) {
		mFunctions = functions;
		for(int i = 0; i < functions.length; i++) {
			FunctionSignature signature = functions[i].getSignature();
			mFunctionMap.put(signature.getFunctionId(), functions[i]);
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

	public Iterator getSignaturesForFunction(QName qname) {
		ArrayList signatures = new ArrayList(2);
		for(int i = 0; i < mFunctions.length; i++) {
			FunctionSignature signature = mFunctions[i].getSignature();
			if(signature.getName().compareTo(qname) == 0)
				signatures.add(signature);
		}
		return signatures.iterator();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getClassForFunction(org.exist.dom.QName)
	 */
	public FunctionDef getFunctionDef(QName qname, int arity) {
		final FunctionId id = new FunctionId(qname, arity);
		return (FunctionDef)mFunctionMap.get(id);
	}
	
	public List getFunctionsByName(QName qname) {
		List funcs = new ArrayList();
		for (Iterator i = mFunctionMap.values().iterator(); i.hasNext(); ) {
			FunctionDef def = (FunctionDef) i.next();
			FunctionSignature sig = def.getSignature();
			if (sig.getName().compareTo(qname) == 0) {
				funcs.add(sig);
			}
		}
		return funcs;
	}
	
	public Variable declareVariable(QName qname, Object value) throws XPathException {
		Sequence val = XPathUtil.javaObjectToXPath(value, null);
		Variable var = (Variable)mGlobalVariables.get(qname);
		if(var == null) {
			var = new Variable(qname);
			mGlobalVariables.put(qname, var);
		}
		var.setValue(val);
		return var;
	}
	
    public Variable declareVariable(Variable var) {
        mGlobalVariables.put(var.getQName(), var);
        return var;
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#resolveVariable(org.exist.dom.QName)
	 */
	public Variable resolveVariable(QName qname) throws XPathException {
		return (Variable)mGlobalVariables.get(qname);
	}
	
    public boolean isVarDeclared(QName qname) {
        return mGlobalVariables.get(qname) != null;
    }
    
	public void reset() {
	}
}
