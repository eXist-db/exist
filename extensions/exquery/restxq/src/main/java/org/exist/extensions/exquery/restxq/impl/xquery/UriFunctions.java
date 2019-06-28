/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.xquery;

import org.exist.dom.QName;
import org.exist.extensions.exquery.restxq.impl.ResourceFunctionExecutorImpl;
import org.exist.extensions.exquery.restxq.impl.adapters.EXQueryErrorCode;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exquery.restxq.RestXqErrorCodes;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class UriFunctions extends BasicFunction {
    
    public final static FunctionSignature signatures[] = {
		
        new FunctionSignature(
            new QName("base-uri", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX),
            "This function returns the implementation defined base URI of the Resource Function.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ONE, "The base URI of the Resource Function.")
        ),
        
        new FunctionSignature(
            new QName("uri", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX),
            "This function is returns the complete URI that addresses the Resource Function. Typically this is the rest:base-uri() appended with the path from the Path Annotation (if present) of the Resource Function.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.ONE, "The URI which addressed the Resource Function.")
        )
    };
    
    public UriFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
        final Variable var;
        if(isCalledAs("base-uri")) {
             var = context.resolveVariable(ResourceFunctionExecutorImpl.XQ_VAR_BASE_URI);
        } else {
            var = context.resolveVariable(ResourceFunctionExecutorImpl.XQ_VAR_URI);
        }

        if(var == null) {
            throw new XPathException(new EXQueryErrorCode(RestXqErrorCodes.RQDY0101), getLine(), getColumn());
        } else {
            return var.getValue();
        }
    }
}