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

import java.util.*;

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

    private String description = "User Defined Module";
    private Map<String, String> metadata = null;

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
        return isReady;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getDescription()
     */
    public String getDescription() {
        return description;
    }

    public void setDescription(String desc) {
        this.description = desc;
    }

    public void addMetadata(String key, String value) {
        if (metadata == null) {
            metadata = new HashMap<String, String>();
        }
        final String old = metadata.get(key);
        if (old != null) {
            value = old + ", " + value;
        }
        metadata.put(key, value);
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.Module#getReleaseVersion()
    */
    public String getReleaseVersion() {
        return "user-defined";
    }

    public UserDefinedFunction getFunction(QName qname, int arity, XQueryContext callerContext) 
    throws XPathException {
        final FunctionId id = new FunctionId(qname, arity);
        final UserDefinedFunction fn = mFunctionMap.get(id);
        if (fn == null)
        	{return null;}
        if (callerContext != getContext() && fn.getSignature().isPrivate()) {
        	throw new XPathException(ErrorCodes.XPST0017, "Calling a private function from outside its module");
        }
        return fn;
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
        final List<FunctionSignature> signatures = new ArrayList<FunctionSignature>(mFunctionMap.size());
        for (final Iterator<UserDefinedFunction> i = mFunctionMap.values().iterator(); i.hasNext(); ) {
        	final FunctionSignature signature = i.next().getSignature();
    		signatures.add(signature);
        }
        final FunctionSignature[] result = new FunctionSignature[signatures.size()];
        return signatures.toArray(result);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getSignatureForFunction(org.exist.dom.QName)
     */
    public Iterator<FunctionSignature> getSignaturesForFunction(QName qname) {
        final ArrayList<FunctionSignature> signatures = new ArrayList<FunctionSignature>(2);
        for (final UserDefinedFunction func : mFunctionMap.values()) {
            if (func.getName().compareTo(qname) == 0)
                {signatures.add(func.getSignature());}
        }
        return signatures.iterator();
    }

    public Iterator<QName> getGlobalVariables() {
        return mGlobalVariables.keySet().iterator();
    }

    public Collection<VariableDeclaration> getVariableDeclarations() {
        return mGlobalVariables.values();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#declareVariable(org.exist.dom.QName, java.lang.Object)
     */
    public Variable declareVariable(QName qname, Object value) throws XPathException {
        final Sequence val = XPathUtil.javaObjectToXPath(value, mContext);
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
            {throw new XPathException(decl, ErrorCodes.XQST0048, "It is a static error" +
                " if a function or variable declared in a library module is" + 
                " not in the target namespace of the library module.");}
        mGlobalVariables.put(qname, decl);
    }

    public boolean isVarDeclared(QName qname) {
        if (mGlobalVariables.get(qname) != null)
            {return true;}
        return mStaticVariables.get(qname) != null;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#resolveVariable(org.exist.dom.QName)
     */
    public Variable resolveVariable(QName qname) throws XPathException {
        final VariableDeclaration decl = mGlobalVariables.get(qname);
        Variable var = mStaticVariables.get(qname);
        if (isReady && decl != null && (var == null || var.getValue() == null)) {
            decl.eval(null);
            var = mStaticVariables.get(qname);
        }
        return var;
    }

    public void analyzeGlobalVars() throws XPathException {
        for (final VariableDeclaration decl : mGlobalVariables.values()) {
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
    protected void setRootExpression(Expression expr) {
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
