/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.modules.request;

import java.util.List;
import java.util.Objects;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exquery.http.HttpRequest;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ParameterFunctions extends AbstractRequestModuleFunction {

    private final static QName qnParameterNames = new QName("parameter-names", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnParameter = new QName("parameter", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    
    public final static FunctionSignature FNS_PARAMETER_NAMES = new FunctionSignature(
        qnParameterNames,
        "Gets the names of parameters available in the HTTP Request.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The names of available parameters from the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_PARAMETER = new FunctionSignature(
        qnParameter,
        "Gets the values of the named parameter from the HTTP Request. If there is no such parameter in the HTTP Request, then an empty sequence is returned.",
        new SequenceType[] {
            new FunctionParameterSequenceType("parameter-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the parameter to retrieve values of.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The value(s) of the named parameter, or an empty sequence.")
    );
    
    public final static FunctionSignature FNS_PARAMETER_WITH_DEFAULT = new FunctionSignature(
        qnParameter,
        "Gets the values of the named parameter from the HTTP Request. If there is no such parameter in the HTTP Request, then the value specified in $default is returned instead.",
        new SequenceType[] {
            new FunctionParameterSequenceType("parameter-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the parameter to retrieve values of."),
            new FunctionParameterSequenceType("default", Type.STRING, Cardinality.ZERO_OR_MORE, "The default value(s) to use if the named parameter is not present in the request.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The value(s) of the named parameter, or the default value(s).")
    );
    
    public ParameterFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(final Sequence[] args, final HttpRequest request) throws XPathException {
        final Sequence result;
        
        if(isCalledAs(qnParameterNames.getLocalPart())) {
            result = new ValueSequence();
            for(final String parameterName : request.getParameterNames()) {
                result.add(new StringValue(this, parameterName));
            }
        } else if(isCalledAs(qnParameter.getLocalPart())) {
            final String paramName = args[0].getStringValue();
            
            if(getSignature().getArgumentCount() == 1) {
                result = getParameter(request, paramName, null);
            } else if(getSignature().getArgumentCount() == 2) {
                final Sequence defaultValues = args[1];
                result = getParameter(request, paramName, defaultValues);
            } else {
                throw new XPathException(this, "Unknown function call: " + getSignature());
            }   
        } else {
            throw new XPathException(this, "Unknown function call: " + getSignature());
        }
        
        return result;
    }
    
    private Sequence getParameter(final HttpRequest request, final String paramName, final Sequence defaultValues) throws XPathException {
        final Sequence result;
        final Object queryParamValues = request.getQueryParam(paramName);
        if(queryParamValues == null) {
            result = Objects.requireNonNullElse(defaultValues, Sequence.EMPTY_SEQUENCE);
        } else {
            result = new ValueSequence();
            if(queryParamValues instanceof List) {
                for(final Object value : (List)queryParamValues) {
                    result.add(new StringValue(this, value.toString()));
                }
            } else {
                result.add(new StringValue(this, queryParamValues.toString()));
            }
        }
        
        return result;
    }
}
