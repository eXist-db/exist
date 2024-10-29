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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * xpath-library function: number(object) $context/number()
 *
 */
public class FunNumber extends Function {

    protected static final String FUNCTION_DESCRIPTION_0_PARAM =    
        "Returns the value of the context item after atomization, " + 
        "converted to an xs:double.\n\n" +
        "If the context item cannot be " +
                "converted to an xs:double, the xs:double value NaN is returned. " +
        "If the context item is undefined an error is raised: " + 
        "[err:XPDY0002]XP.\n\n";	
	
    protected static final String FUNCTION_DESCRIPTION_1_PARAM =
        "Returns the value indicated by $arg or, if $arg is not specified, " +
        "the context item after atomization, converted to an xs:double.\n\n" +
        "Calling the zero-argument version of the function is defined to " +
        "give the same result as calling the single-argument version with " +
        "the context item (.). That is, fn:number() is equivalent to fn:number(.).\n\n" +
        "If $arg is the empty sequence or if $arg or the context item cannot be " +
        "converted to an xs:double, the xs:double value NaN is returned. " +
        "If the " +
        "context item is undefined an error is raised: [err:XPDY0002]XP.\n\n" +
        "If $arg is the empty sequence, NaN is returned. Otherwise, $arg, or " +
        "the context item after atomization, is converted to an xs:double " +
        "following the rules of 17.1.3.2 Casting to xs:double. If the conversion " +
        "to xs:double fails, the xs:double value NaN is returned.";

    protected static final FunctionParameterSequenceType ARG_PARAM = new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "The input item");
    protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE, "the numerical value");

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("number", Function.BUILTIN_FUNCTION_NS),
            FUNCTION_DESCRIPTION_0_PARAM,
            new SequenceType[0],
            RETURN_TYPE
        ),
        new FunctionSignature(
            new QName("number", Function.BUILTIN_FUNCTION_NS),
            FUNCTION_DESCRIPTION_1_PARAM,
            new SequenceType[] { ARG_PARAM },
            RETURN_TYPE
        )
    };

    public FunNumber(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if(context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if(contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if(contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }
        
        if(contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        
        Sequence result;
        Sequence arg = null;
        
        if(getSignature().getArgumentCount() == 1) {
            //value is from $arg
            arg = getArgument(0).eval(contextSequence, null);
            
            if(arg.isEmpty()) {
                result = DoubleValue.NaN;
            } else {
                try {
                    result = arg.convertTo(Type.DOUBLE);
                } catch(final XPathException e) {
                    result = DoubleValue.NaN;
                }
            }
        } else {
            //value is from $context
            if(contextSequence == null) {
                throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");
            }
            arg = contextSequence;
            
            if(arg.isEmpty()) {
                result = Sequence.EMPTY_SEQUENCE;
            } else {
                try {
                    result = arg.convertTo(Type.DOUBLE);
                } catch(final XPathException e) {
                    result = DoubleValue.NaN;
                }
            }
        }
        
        if(context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result); 
        }
        
        return result; 
        
    }
}
