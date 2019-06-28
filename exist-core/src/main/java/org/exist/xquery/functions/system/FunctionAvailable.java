/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
 *  $Id:
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.InternalModule;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Return whether the function is available
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class FunctionAvailable extends BasicFunction {

    protected final static Logger logger = LogManager.getLogger(FunctionAvailable.class);
    public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("function-available", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns whether a function is available.",
            new SequenceType[]{
                new FunctionParameterSequenceType("function-name", Type.QNAME, Cardinality.ONE, "The fully qualified name of the function"),
                new FunctionParameterSequenceType("arity", Type.INTEGER, Cardinality.ONE, "The arity of the function")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the function exists, false() otherwise."));

    public FunctionAvailable(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        
        final QName functionName = ((QNameValue)args[0].itemAt(0)).getQName();
        final int arity = ((IntegerValue)args[1].itemAt(0)).getInt();
        
        final Module module = context.getModule(functionName.getNamespaceURI());
        boolean found = false;
        if(module == null) {
            found = context.resolveFunction(functionName, arity) != null;
        } else {
            if(module instanceof InternalModule) {
                 found = ((InternalModule)module).getFunctionDef(functionName, arity) != null;
            } else if(module instanceof ExternalModule) {
                found = ((ExternalModule)module).getFunction(functionName, arity, context) != null;
            } 
        }
        
        return BooleanValue.valueOf(found);
    }    
}