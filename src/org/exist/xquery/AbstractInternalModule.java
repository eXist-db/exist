/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
 * @author ljo
 *
 */
public abstract class AbstractInternalModule implements InternalModule {

    public static class FunctionComparator implements Comparator<FunctionDef> {
        @Override
        public int compare(FunctionDef o1, FunctionDef o2) {
            return o1.getSignature().getFunctionId().compareTo(o2.getSignature().getFunctionId());
        }
    };

    protected final FunctionDef[] mFunctions;
    protected final boolean ordered;
    private final Map<String, List<? extends Object>> parameters;

    protected final TreeMap<QName, Variable> mGlobalVariables = new TreeMap<QName, Variable>();

    public AbstractInternalModule(FunctionDef[] functions,
            Map<String, List<? extends Object>> parameters) {
        this(functions, parameters, false);
    }

    public AbstractInternalModule(FunctionDef[] functions, Map<String,
            List<? extends Object>> parameters, boolean functionsOrdered) {
        this.mFunctions = functions;
        this.ordered = functionsOrdered;
        this.parameters = parameters;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#isInternalModule()
     */
    @Override
    public boolean isInternalModule() {
        return true;
    }

    /**
     * returns a module parameter
     */
    protected List<? extends Object> getParameter(String paramName) {
        return parameters.get(paramName);
    }

    @Override
    public boolean isReady() {
        return true; // internal modules don't need to be compiled
    }

    @Override
    public FunctionSignature[] listFunctions() {
        final FunctionSignature signatures[] = new FunctionSignature[mFunctions.length];
        for (int i = 0; i < signatures.length; i++) {
            signatures[i] = mFunctions[i].getSignature();
        }
        return signatures;
    }

    @Override
    public Iterator<FunctionSignature> getSignaturesForFunction(QName qname) {
        final List<FunctionSignature> signatures = new ArrayList<FunctionSignature>(2);
        for (int i = 0; i < mFunctions.length; i++) {
            final FunctionSignature signature = mFunctions[i].getSignature();
            if (signature.getName().compareTo(qname) == 0){
                signatures.add(signature);
            }
        }
        return signatures.iterator();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#getClassForFunction(org.exist.dom.QName)
     */
    @Override
    public FunctionDef getFunctionDef(QName qname, int arity) {
        final FunctionId id = new FunctionId(qname, arity);
        if (ordered) {
            return binarySearch(id);
        } else {
            for (int i = 0; i < mFunctions.length; i++) {
                if (id.compareTo(mFunctions[i].getSignature().getFunctionId()) == 0) {
                    return mFunctions[i];
                }
            }
        }
        return null;
    }

    private FunctionDef binarySearch(FunctionId id) {
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
    public List<FunctionSignature> getFunctionsByName(QName qname) {
        final List<FunctionSignature> funcs = new ArrayList<FunctionSignature>();
        for (int i = 0; i < mFunctions.length; i++) {
            final FunctionSignature sig = mFunctions[i].getSignature();
            if (sig.getName().compareTo(qname) == 0) {
                funcs.add(sig);
            }
        }
        return funcs;
    }

    public Iterator<QName> getGlobalVariables() {
        return mGlobalVariables.keySet().iterator();
    }

    @Override
    public Variable declareVariable(QName qname, Object value) throws XPathException {
        final Sequence val = XPathUtil.javaObjectToXPath(value, null);
        Variable var = mGlobalVariables.get(qname);
        if (var == null){
            var = new VariableImpl(qname);
            mGlobalVariables.put(qname, var);
        }
        var.setValue(val);
        return var;
    }

    @Override
    public Variable declareVariable(Variable var) {
        mGlobalVariables.put(var.getQName(), var);
        return var;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Module#resolveVariable(org.exist.dom.QName)
     */
    @Override
    public Variable resolveVariable(QName qname) throws XPathException {
        return mGlobalVariables.get(qname);
    }

    @Override
    public boolean isVarDeclared(QName qname) {
        return mGlobalVariables.get(qname) != null;
    }

    @Override
    public void reset(XQueryContext xqueryContext) {
        //Nothing to do
    }
}