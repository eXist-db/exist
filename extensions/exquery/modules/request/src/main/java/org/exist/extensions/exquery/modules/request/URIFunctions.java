/*
Copyright (c) 2013, Adam Retter
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
package org.exist.extensions.exquery.modules.request;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exquery.http.HttpRequest;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class URIFunctions extends AbstractRequestModuleFunction {

    private final static QName qnScheme = new QName("scheme", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnHostname = new QName("hostname", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnPort = new QName("port", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnPath = new QName("path", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnQuery = new QName("query", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnUri = new QName("uri", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    
    public final static FunctionSignature FNS_SCHEME = new FunctionSignature(
        qnScheme,
        "Gets the Scheme of the HTTP Request e.g. https.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "The Scheme of the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_HOSTNAME = new FunctionSignature(
        qnHostname,
        "Gets the Hostname fragment of the Authority component of the URI of the HTTP Request.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "The Hostname of the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_PORT = new FunctionSignature(
        qnPort,
        "Gets the Port fragment of the Authority component of the URI of the HTTP Request. If the port is not explicitly specified in the URI, then the default port for the HTTP Scheme is returned (i.e. 21 for FTP, 80 for HTTP and 443 for HTTPS).",
        null,
        new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ONE, "The Port of the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_PATH = new FunctionSignature(
        qnPath,
        "Gets the Path component of the URI of the HTTP Request.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "The Path of the URI of the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_QUERY = new FunctionSignature(
        qnQuery,
        "Gets the Query Component of the HTTP Request URI, if there is no query component then an empty sequence is returned.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The Query of the URI of the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_URI = new FunctionSignature(
        qnUri,
        "Gets the URI of the HTTP Request URI.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The URI of the HTTP Request.")
    );
    
    public URIFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(final Sequence[] args, final HttpRequest request) throws XPathException {
        final Sequence result;
        
        if(isCalledAs(qnScheme.getLocalPart())) {
            result = new StringValue(request.getScheme());
        
        } else if(isCalledAs(qnHostname.getLocalPart())) {
            result = new StringValue(request.getHostname());
        
        } else if(isCalledAs(qnPort.getLocalPart())) {
            result = new IntegerValue(request.getPort());

        } else if(isCalledAs(qnPath.getLocalPart())) {
            result = new StringValue(request.getPath());
            
        } else if(isCalledAs(qnQuery.getLocalPart())) {
            final String query = request.getQuery();
            if(query == null) {
                result = Sequence.EMPTY_SEQUENCE;
            } else {
                result = new StringValue(query);
            }
        } else if(isCalledAs(qnUri.getLocalPart())) {
            result = new StringValue(request.getURI());
            
        } else {
            throw new XPathException(this, "Unknown function call: " + getSignature());
        }
        
        return result;
    }
}
