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
import java.util.TreeMap;

import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.xquery.value.Sequence;

/**
 * Default implementation of an {@link org.exist.xquery.ExternalModule}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ExternalModuleImpl implements ExternalModule {

	final private String mNamespaceURI;
	final private String mPrefix;

	final private TreeMap mFunctionMap = new TreeMap();
	final private TreeMap mGlobalVariables = new TreeMap();
	final private TreeMap mStaticVariables = new TreeMap();

	private Source mSource = null;
	
	private XQueryContext mContext = null;
	
	public ExternalModuleImpl(String namespaceURI, String prefix) {
		mNamespaceURI = namespaceURI;
		mPrefix = prefix;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "User defined module";
	}
	
	public UserDefinedFunction getFunction(QName qname, int arity) {
		FunctionId id = new FunctionId(qname, arity);
		return (UserDefinedFunction) mFunctionMap.get(id);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.ExternalModule#declareFunction(org.exist.xquery.UserDefinedFunction)
	 */
	public void declareFunction(UserDefinedFunction func) {
		mFunctionMap.put(func.getSignature().getFunctionId(), func);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return mNamespaceURI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return mPrefix;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#isInternalModule()
	 */
	public boolean isInternalModule() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#listFunctions()
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
	 * @see org.exist.xquery.Module#getSignatureForFunction(org.exist.dom.QName)
	 */
	public Iterator getSignaturesForFunction(QName qname) {
		ArrayList signatures = new ArrayList(2);
		for (Iterator i = mFunctionMap.values().iterator(); i.hasNext(); ) {
			UserDefinedFunction func = (UserDefinedFunction) i.next();
			if(func.getName().compareTo(qname) == 0)
				signatures.add(func.getSignature());
		}
		return signatures.iterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#declareVariable(org.exist.dom.QName, java.lang.Object)
	 */
	public Variable declareVariable(QName qname, Object value) throws XPathException {
		Sequence val = XPathUtil.javaObjectToXPath(value, mContext);
		Variable var = (Variable) mStaticVariables.get(qname);
		if (var == null) {
			var = new Variable(qname);
			mStaticVariables.put(qname, var);
		}
		var.setValue(val);
		return var;
	}

    public Variable declareVariable(Variable var) {
        mStaticVariables.put(var.getQName(), var);
        return var;
    }
    
	public void declareVariable(QName qname, VariableDeclaration decl) throws XPathException {
        if (!qname.getNamespaceURI().equals(getNamespaceURI()))
            throw new XPathException(decl.getASTNode(), "err:XQST0048: It is a static error if a function " +
                "or variable declared in a library module is not in the target namespace of the library module.");
		mGlobalVariables.put(qname, decl);
	}
	
    public boolean isVarDeclared(QName qname) {
        if (mGlobalVariables.get(qname) != null)
            return true;
        return mStaticVariables.get(qname) != null;
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#resolveVariable(org.exist.dom.QName)
	 */
	public Variable resolveVariable(QName qname) throws XPathException {
		VariableDeclaration decl = (VariableDeclaration)mGlobalVariables.get(qname);
		if(decl != null && !mStaticVariables.containsKey(qname)) {
            decl.eval(null);
		}
		return (Variable) mStaticVariables.get(qname);
	}
	
	public Source getSource() {
		return mSource;
	}
	
	public void setSource(Source source) {
		mSource = source;
	}
	
	public void setContext(XQueryContext context) {
		mContext = context;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.ExternalModule#moduleIsValid()
	 */
	public boolean moduleIsValid() {
		if(mSource.isValid(mContext.getBroker()) != Source.VALID)
			return false;
		// check other modules imported from here
		return mContext.checkModulesValid();
	}
	
	public void reset(XQueryContext xqueryContext) {
        mContext.reset();
        mStaticVariables.clear();
    }
}
