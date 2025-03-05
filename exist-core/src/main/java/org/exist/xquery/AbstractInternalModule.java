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
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

/**
 * Abstract base class for an {@link org.exist.xquery.InternalModule}.
 * Functions are defined in an array of {@link org.exist.xquery.FunctionDef}, which
 * is passed to the constructor. A single implementation class
 * can be registered for more than one function signature, given that the signatures differ
 * in name or the number of expected arguments. It is thus possible to implement
 * similar XQuery functions in one single class.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author ljo
 */
public abstract class AbstractInternalModule implements InternalModule {

    public static class FunctionComparator implements Comparator<FunctionDef> {
        @Override
        public int compare(final FunctionDef o1, final FunctionDef o2) {
            return o1.getSignature().getFunctionId().compareTo(o2.getSignature().getFunctionId());
        }
    }

    protected final FunctionDef[] mFunctions;
    protected final boolean ordered;
    private final Map<String, List<?>> parameters;

    protected final Map<QName, Variable> mGlobalVariables = new HashMap<>();

    public AbstractInternalModule(final FunctionDef[] functions, final Map<String, List<?>> parameters) {
        this(functions, parameters, false);
    }

    public AbstractInternalModule(final FunctionDef[] functions, final Map<String, List<?>> parameters,
                                  final boolean functionsOrdered) {
        this.mFunctions = functions;
        this.ordered = functionsOrdered;
        this.parameters = parameters;
    }

    @Override
    public boolean isInternalModule() {
        return true;
    }

    /**
     * Get a parameter.
     *
     * @param paramName the name of the parameter
     * @return the value of tyhe parameter
     */
    protected List<?> getParameter(final String paramName) {
        return parameters.get(paramName);
    }

    @Override
    public void setContextItem(final Sequence contextItem) {
        // not used for internal modules
    }

    @Override
    public boolean isReady() {
        return true; // internal modules don't need to be compiled
    }

    @Override
    public FunctionSignature[] listFunctions() {
        final FunctionSignature[] signatures = new FunctionSignature[mFunctions.length];
        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = mFunctions[i].getSignature();
        }
        return signatures;
    }

    @Override
    public Iterator<FunctionSignature> getSignaturesForFunction(final QName qname) {
        final List<FunctionSignature> signatures = new ArrayList<>(2);
        for (FunctionDef mFunction : mFunctions) {
            final FunctionSignature signature = mFunction.getSignature();
            if (signature.getName().compareTo(qname) == 0) {
                signatures.add(signature);
            }
        }
        return signatures.iterator();
    }

    @Override
    public FunctionDef getFunctionDef(QName qname, int arity) {
        final FunctionId id = new FunctionId(qname, arity);
        if (ordered) {
            return binarySearch(id);
        } else {
            for (FunctionDef mFunction : mFunctions) {
                if (id.compareTo(mFunction.getSignature().getFunctionId()) == 0) {
                    return mFunction;
                }
            }
        }
        return null;
    }

    private FunctionDef binarySearch(final FunctionId id) {
        int low = 0;
        int high = mFunctions.length - 1;

        while (low <= high) {
            final int mid = (low + high) >>> 1;
            final FunctionDef midVal = mFunctions[mid];
            final int cmp = midVal.getSignature().getFunctionId().compareTo(id);
            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return midVal; // key found
            }
        }
        return null; // key not found.
    }

    @Override
    public List<FunctionSignature> getFunctionsByName(final QName qname) {
        final List<FunctionSignature> funcs = new ArrayList<>();
        for (FunctionDef mFunction : mFunctions) {
            final FunctionSignature sig = mFunction.getSignature();
            if (sig.getName().compareTo(qname) == 0) {
                funcs.add(sig);
            }
        }
        return funcs;
    }

    public Iterator<QName> getGlobalVariables() {
        return mGlobalVariables.keySet().iterator();
    }

    /**
     * Declares a variable defined by the module.
     * <p>
     * NOTE: this should not be called from the constructor of a module
     * otherwise when {@link #reset(XQueryContext, boolean)} is called
     * with {@code keepGlobals = false}, the variables will be removed
     * from the module. Which means they will not be available
     * for subsequent re-executions of a cached XQuery.
     * Instead, module level variables should be initialised
     * in {@link #prepare(XQueryContext)}.
     *
     * @param qname The name of the variable
     * @param value The Java value of the variable, will be converted to an XDM type.
     * @return the variable
     */
    @Override
    public Variable declareVariable(final QName qname, final Object value) throws XPathException {
        final Sequence val = XPathUtil.javaObjectToXPath(value, null, null);
        Variable var = mGlobalVariables.computeIfAbsent(qname, VariableImpl::new);
        var.setValue(val);
        return var;
    }

    /**
     * Declares a variable defined by the module.
     * <p>
     * NOTE: this should not be called from the constructor of a module
     * otherwise when {@link #reset(XQueryContext, boolean)} is called
     * with {@code keepGlobals = false}, the variables will be removed
     * from the module. Which means they will not be available
     * for subsequent re-executions of a cached XQuery.
     * Instead, module level variables should be initialised
     * in {@link #prepare(XQueryContext)}.
     *
     * @param var The variable
     * @return the variable
     */
    @Override
    public Variable declareVariable(final Variable var) {
        mGlobalVariables.put(var.getQName(), var);
        return var;
    }

    @Override
    @Nullable
    public Variable resolveVariable(final QName qname) throws XPathException {
        return resolveVariable(null, qname);
    }

    @Override
    @Nullable
    public Variable resolveVariable(@Nullable final AnalyzeContextInfo contextInfo, final QName qname) throws XPathException {
        return mGlobalVariables.get(qname);
    }

    @Override
    public boolean isVarDeclared(final QName qname) {
        return mGlobalVariables.get(qname) != null;
    }

    @Override
    public void reset(final XQueryContext context) {
        //Nothing to do
    }

    @Override
    public void reset(final XQueryContext xqueryContext, final boolean keepGlobals) {
        // call deprecated method for backwards compatibility
        reset(xqueryContext);

        if (!keepGlobals) {
            mGlobalVariables.clear();
        }
    }
}