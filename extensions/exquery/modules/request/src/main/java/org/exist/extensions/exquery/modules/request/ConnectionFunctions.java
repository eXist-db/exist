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
public class ConnectionFunctions extends AbstractRequestModuleFunction {

    private final static QName qnAddress = new QName("address", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnRemoteHostname = new QName("remote-hostname", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnRemoteAddress = new QName("remote-address", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    private final static QName qnRemotePort = new QName("remote-port", RequestModule.NAMESPACE_URI, RequestModule.PREFIX);
    
    public final static FunctionSignature FNS_ADDRESS = new FunctionSignature(
        qnAddress,
        "Gets the IP address of the server that received the HTTP Request",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "The IP address of the server.")
    );
    
    public final static FunctionSignature FNS_REMOTE_HOSTNAME = new FunctionSignature(
        qnRemoteHostname,
        "Gets the fully qualified hostname of the client or the last proxy that sent the HTTP Request. If the name of the remote host cannot be established, this method behaves as request:remote-address(), and returns the IP address.",
        null,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "The Hostname of the client that issues the HTTP Request.")
    );
    
    public final static FunctionSignature FNS_REMOTE_ADDRESS = new FunctionSignature(
        qnRemoteAddress,
        "Gets the IP address of the client or the last proxy that sent the HTTP Request.",
        null,
        new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ONE, "The IP address of the client.")
    );
    
    public final static FunctionSignature FNS_REMOTE_PORT = new FunctionSignature(
        qnRemotePort,
        "Gets the TCP port number of the client socket or the last proxy that sent the HTTP Request..",
        null,
        new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ONE, "The TCP port number of the client.")
    );
    
    public ConnectionFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(final Sequence[] args, final HttpRequest request) throws XPathException {
        final Sequence result;
        
        if(isCalledAs(qnAddress.getLocalPart())) {
            result = new StringValue(request.getAddress());
        
        } else if(isCalledAs(qnRemoteHostname.getLocalPart())) {
            result = new StringValue(request.getRemoteHostname());
        
        } else if(isCalledAs(qnRemoteAddress.getLocalPart())) {
            result = new StringValue(request.getRemoteAddress());

        } else if(isCalledAs(qnRemotePort.getLocalPart())) {
            result = new IntegerValue(request.getRemotePort());
               
        } else {
            throw new XPathException(this, "Unknown function call: " + getSignature());
        }
        
        return result;
    }
}
