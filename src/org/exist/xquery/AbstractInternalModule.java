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
 * @author ljo
 *
 */
public abstract class AbstractInternalModule implements InternalModule {

	private final static Logger LOG = Logger.getLogger(AbstractInternalModule.class);

    public static class FunctionComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            FunctionDef d1 = (FunctionDef) o1;
            return d1.getSignature().getFunctionId().compareTo(((FunctionDef) o2).getSignature().getFunctionId());
        }
    };

    protected FunctionDef[] mFunctions;

    protected boolean ordered = false;

    protected final TreeMap mGlobalVariables = new TreeMap();

    public AbstractInternalModule(FunctionDef[] functions) {
        this(functions, false);
    }

    public AbstractInternalModule(FunctionDef[] functions, boolean functionsOrdered) {
		this.mFunctions = functions;
        this.ordered = functionsOrdered;
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

    /**
     * Module description for the documentation.
     * This is a short phrase describing the module, starting with:
     * A module for. 
     */
    public abstract String getDescription();

    /**
     * Module release version for the documentation. Since a module can live 
     * in trunk a long time before it is included in a release,
     * this is neccessary for the documentation.
     */
    public abstract String getReleaseVersion();

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
        FunctionId id = new FunctionId(qname, arity);
        if (ordered) {
            return binarySearch(id);
        } else {
            for (int i = 0; i < mFunctions.length; i++) {
                if (id.compareTo(mFunctions[i].getSignature().getFunctionId()) == 0)
                    return mFunctions[i];
            }
        }
        return null;
    }

    private FunctionDef binarySearch(FunctionId id) {
        int low = 0;
        int high = mFunctions.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            FunctionDef midVal = mFunctions[mid];
            int cmp = midVal.getSignature().getFunctionId().compareTo(id);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return midVal; // key found
        }
        return null;  // key not found.
    }

    public List getFunctionsByName(QName qname) {
		List funcs = new ArrayList();
		for (int i = 0; i < mFunctions.length; i++) {
			FunctionSignature sig = mFunctions[i].getSignature();
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

    public void reset(XQueryContext xqueryContext) {
	}
}
