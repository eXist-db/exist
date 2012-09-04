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
import org.exist.extensions.exquery.restxq.impl.RestXqServiceRegistryManager;
import org.exist.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RegistryFunctions extends BasicFunction {
    
    public final static QName RESOURCE_FUNCTIONS = new QName("resource-functions", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static QName RESOURCE_FUNCTION = new QName("resource-function", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static String XQUERY_URI = "xquery-uri";
    public final static QName RESOURCE_FUNCTION_IDENTITY = new QName("identity", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static String NAMESPACE = "namespace";
    public final static String LOCAL_NAME = "local-name";
    public final static String ARITY = "arity";
    
    public final static FunctionSignature signatures[] = {
		
        new FunctionSignature(
            new QName("resource-functions", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX),
            "Gets a list of all the registered resource functions.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The list of registered resource functions.")
        )
    };
    
    public RegistryFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
        final RestXqServiceRegistry registry = RestXqServiceRegistryManager.getRegistry(getContext().getBroker().getBrokerPool());
        
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();
        builder.startElement(RESOURCE_FUNCTIONS, null);
        
        for(final RestXqService service : registry) {
            
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(null, XQUERY_URI, "", "string", service.getResourceFunction().getXQueryLocation().toString());
            builder.startElement(RESOURCE_FUNCTION, attrs);
            
            //identity
            attrs = new AttributesImpl();
            attrs.addAttribute(null, NAMESPACE, "", "string", service.getResourceFunction().getFunctionSignature().getName().getNamespaceURI());
            attrs.addAttribute(null, LOCAL_NAME, "", "string", service.getResourceFunction().getFunctionSignature().getName().getLocalPart());
            attrs.addAttribute(null, ARITY, "", "int", Integer.toString(service.getResourceFunction().getFunctionSignature().getArgumentCount()));
            builder.startElement(RESOURCE_FUNCTION_IDENTITY, attrs);
            builder.endElement();
            
            //TODO annotations
            
            
            builder.endElement();
        }
        
        builder.endElement();
        builder.endDocument();
        
        
        return (NodeValue)builder.getDocument().getDocumentElement();
    }
}