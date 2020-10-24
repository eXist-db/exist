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
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

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
                new FunctionParameterSequenceType("function-name", Type.QNAME, Cardinality.EXACTLY_ONE, "The fully qualified name of the function"),
                new FunctionParameterSequenceType("arity", Type.INTEGER, Cardinality.EXACTLY_ONE, "The arity of the function")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the function exists, false() otherwise."));

    public FunctionAvailable(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final QName functionName = ((QNameValue)args[0].itemAt(0)).getQName();
        final int arity = ((IntegerValue)args[1].itemAt(0)).getInt();
        
        final Module[] modules = context.getModules(functionName.getNamespaceURI());
        boolean found = false;
        if (isEmpty(modules)) {
            found = context.resolveFunction(functionName, arity) != null;
        } else {
            for (final Module module : modules) {
                if(module instanceof InternalModule) {
                    found = ((InternalModule)module).getFunctionDef(functionName, arity) != null;
                } else if(module instanceof ExternalModule) {
                    found = ((ExternalModule)module).getFunction(functionName, arity, context) != null;
                }

                if (found) {
                    break;
                }
            }
        }
        
        return BooleanValue.valueOf(found);
    }    
}