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
import org.exist.storage.DBBroker;

/**
 * Default implementation of an {@link org.exist.xquery.ExternalModule}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ExternalModuleImpl implements ExternalModule {

	private String mNamespaceURI;
	private String mPrefix;

    private boolean isReady = false;

	final private TreeMap<FunctionId, UserDefinedFunction> mFunctionMap = new TreeMap<FunctionId, UserDefinedFunction>();
	final private TreeMap<QName, VariableDeclaration> mGlobalVariables = new TreeMap<QName, VariableDeclaration>();
	final private TreeMap<QName, Variable> mStaticVariables = new TreeMap<QName, Variable>();

	private Source mSource = null;
	
	private XQueryContext mContext = null;
	
	public ExternalModuleImpl(String namespaceURI, String prefix) {
		mNamespaceURI = namespaceURI;
		mPrefix = prefix;
	}

    public void setNamespace(String prefix, String namespace) {
        this.mPrefix = prefix;
        this.mNamespaceURI = namespace;
    }

    public void setIsReady(boolean ready) {
        this.isReady = ready;
    }
    
    public boolean isReady() {
        return false;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "User defined module";
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getReleaseVersion()
	 */
	public String getReleaseVersion() {
		return "user-defined";
	}

	
	public UserDefinedFunction getFunction(QName qname, int arity) {
		FunctionId id = new FunctionId(qname, arity);
		return mFunctionMap.get(id);
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
		for (Iterator<UserDefinedFunction> i = mFunctionMap.values().iterator(); i.hasNext(); j++) {
			signatures[j] = i.next().getSignature();
		}
		return signatures;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getSignatureForFunction(org.exist.dom.QName)
	 */
	public Iterator<FunctionSignature> getSignaturesForFunction(QName qname) {
		ArrayList<FunctionSignature> signatures = new ArrayList<FunctionSignature>(2);
		for (UserDefinedFunction func : mFunctionMap.values()) {
			if(func.getName().compareTo(qname) == 0)
				signatures.add(func.getSignature());
		}
		return signatures.iterator();
	}

	public Iterator<QName> getGlobalVariables() {
		return mGlobalVariables.keySet().iterator();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#declareVariable(org.exist.dom.QName, java.lang.Object)
	 */
	public Variable declareVariable(QName qname, Object value) throws XPathException {
		Sequence val = XPathUtil.javaObjectToXPath(value, mContext);
		Variable var = mStaticVariables.get(qname);
		if (var == null) {
			var = new VariableImpl(qname);
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
            throw new XPathException(decl, ErrorCodes.XQST0048, "It is a static error if a function " +
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
		VariableDeclaration decl = mGlobalVariables.get(qname);
        Variable var = mStaticVariables.get(qname);
		if(isReady && decl != null && (var == null || var.getValue() == null)) {
            decl.eval(null);
            var = mStaticVariables.get(qname);
		}
		return var;
	}

    public void analyzeGlobalVars() throws XPathException {
        for (VariableDeclaration decl : mGlobalVariables.values()) {
            decl.resetState(false);
            decl.analyze(new AnalyzeContextInfo());
        }
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
	
	public XQueryContext getContext() {
		return mContext;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.ExternalModule#moduleIsValid()
	 */
	public boolean moduleIsValid(DBBroker broker) {
		return mSource != null && mSource.isValid(broker) == Source.VALID;
//		return (mSource.isValid(broker) == Source.VALID && mContext.checkModulesValid());
	}
	
	public void reset(XQueryContext xqueryContext) {
        mContext.reset();
        mStaticVariables.clear();
    }
	
	private Expression rootExpression = null;

	/**
     * Set the root expression for this context.
     *
     * @param  expr
     */
    protected void setRootExpression( Expression expr ) {
        rootExpression = expr;
    }


    /**
     * Returns the root expression associated with this context.
     *
     * @return  root expression
     */
    public Expression getRootExpression() {
        return  rootExpression;
    }

}
