/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import java.util.*;

import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

/**
 * Default implementation of an {@link org.exist.xquery.ExternalModule}.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class ExternalModuleImpl implements ExternalModule {

    private String mNamespaceURI;
    private String mPrefix;

    private String description = "User Defined Module";
    private Map<String, String> metadata = null;

    private boolean isReady = false;

    final private TreeMap<FunctionId, UserDefinedFunction> mFunctionMap = new TreeMap<>();
    final private TreeMap<QName, VariableDeclaration> mGlobalVariables = new TreeMap<>();
    final private TreeMap<QName, Variable> mStaticVariables = new TreeMap<>();

    private Source mSource = null;

    private XQueryContext mContext = null;

    private boolean needsReset = true;

    public ExternalModuleImpl(String namespaceURI, String prefix) {
        mNamespaceURI = namespaceURI;
        mPrefix = prefix;
    }

    public void setNamespace(String prefix, String namespace) {
        this.mPrefix = prefix;
        this.mNamespaceURI = namespace;
    }

    @Override
    public void setContextItem(Sequence contextItem) {
        mContext.setContextItem(contextItem);
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
            metadata = new HashMap<>();
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
        	throw new XPathException(fn, ErrorCodes.XPST0017, "Calling a private function from outside its module");
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
        final List<FunctionSignature> signatures = new ArrayList<>(mFunctionMap.size());
        for (UserDefinedFunction userDefinedFunction : mFunctionMap.values()) {
            final FunctionSignature signature = userDefinedFunction.getSignature();
            signatures.add(signature);
        }
        final FunctionSignature[] result = new FunctionSignature[signatures.size()];
        return signatures.toArray(result);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getSignatureForFunction(org.exist.dom.QName)
     */
    public Iterator<FunctionSignature> getSignaturesForFunction(QName qname) {
        final ArrayList<FunctionSignature> signatures = new ArrayList<>(2);
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
        final Sequence val = XPathUtil.javaObjectToXPath(value, mContext, null);
        Variable var = mStaticVariables.computeIfAbsent(qname, VariableImpl::new);
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

    @Override
    @Nullable public Variable resolveVariable(final QName qname) throws XPathException {
        return resolveVariable(null, qname);
    }

    @Override
    @Nullable public Variable resolveVariable(@Nullable final AnalyzeContextInfo contextInfo, final QName qname) throws XPathException {
        final VariableDeclaration decl = mGlobalVariables.get(qname);
        Variable var = mStaticVariables.get(qname);
        if (isReady && decl != null && (var == null || var.getValue() == null)) {

            // Make sure Analyze has been called, see - https://github.com/eXist-db/exist/issues/4096
            final AnalyzeContextInfo declContextInfo;
            if (contextInfo != null) {
                declContextInfo = new AnalyzeContextInfo(contextInfo);
            } else {
                declContextInfo = new AnalyzeContextInfo();
            }
            decl.analyze(declContextInfo);

            decl.eval(getContext().getContextItem(), null);
            var = mStaticVariables.get(qname);
        }
        if (var == null) {
            // external variable may be defined in root context importing this module
            final Variable rootVar = getContext().getRootContext().resolveGlobalVariable(qname);
            if (rootVar != null) {
                var = declareVariable(rootVar);
            }
        }
        // set sequence type if decl != null (might be null if called by parser before declaration)
        if (var != null && decl != null) {
            var.setSequenceType(decl.getSequenceType());
        }
        return var;
    }

    public void analyzeGlobalVars() throws XPathException {
        for (final VariableDeclaration decl : mGlobalVariables.values()) {
            decl.analyzeExpression(new AnalyzeContextInfo());
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

    @Override
    public boolean moduleIsValid() {
        return mSource != null && mSource.isValid() == Source.Validity.VALID;
    }

    @Override
    public void reset(XQueryContext context) {
        // deprecated, ignore
    }

    public void reset(XQueryContext xqueryContext, boolean keepGlobals) {
        // prevent recursive calls by checking needsReset
        if (needsReset) {
            needsReset = false;
            mContext.reset(keepGlobals);
            if (!keepGlobals) {
                mStaticVariables.clear();
                // reset state of variable declarations
                mGlobalVariables.values().forEach(v -> v.resetState(true));
            }
            needsReset = true;
        }
    }

    private Expression rootExpression = null;

    /**
     * Set the root expression for this context.
     *
     * @param  expr the root expression
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
