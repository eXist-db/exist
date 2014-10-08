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
package org.exist.extensions.exquery.restxq.impl.xquery.exist;


import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.extensions.exquery.restxq.impl.ExistXqueryRegistry;
import org.exist.extensions.exquery.restxq.impl.RestXqServiceRegistryManager;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RegistryFunctions extends BasicFunction {
    
    private final static QName qnFindResourceFunctions = new QName("find-resource-functions", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnRegisterModule = new QName("register-module", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnDeregisterModule = new QName("deregister-module", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnRegisterResourceFunction = new QName("register-resource-function", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnDeregisterResourceFunction = new QName("deregister-resource-function", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    
    private final static SequenceType PARAM_MODULE = new FunctionParameterSequenceType("module", Type.ANY_URI, Cardinality.EXACTLY_ONE, "A URI pointing to an XQuery module.");
    private final static SequenceType PARAM_RESOURCE_FUNCTION = new FunctionParameterSequenceType("function-signature", Type.STRING, Cardinality.EXACTLY_ONE, "A signature identifying a resource function. Takes the format {namespace}local-name#arity e.g. {http://somenamespace}some-function#2");
        
    public final static FunctionSignature FNS_REGISTER_MODULE = new FunctionSignature(
        qnRegisterModule,
        "Registers all resource functions identified in the XQuery Module with the RestXQ Registry.",
        new SequenceType[]{
            PARAM_MODULE
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The list of newly registered resource functions.")
    );
        
    public final static FunctionSignature FNS_DEREGISTER_MODULE = new FunctionSignature(
        qnDeregisterModule,
        "Deregisters all resource functions identified in the XQuery Module from the RestXQ Registry.",
        new SequenceType[]{
            PARAM_MODULE
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The list of deregistered resource functions.")
    );
        
    public final static FunctionSignature FNS_FIND_RESOURCE_FUNCTIONS = new FunctionSignature(
        qnFindResourceFunctions,
        "Compiles the XQuery Module and examines it, producing a list of all the declared resource functions.",
        new SequenceType[]{
            PARAM_MODULE
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The list of newly registered resource functions.")
    );
        
    public final static FunctionSignature FNS_REGISTER_RESOURCE_FUNCTION = new FunctionSignature(
        qnRegisterResourceFunction,
        "Registers a resource function from the XQuery Module with the RestXQ Registry.",
        new SequenceType[]{
            PARAM_MODULE,
            PARAM_RESOURCE_FUNCTION,
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "true if the function was registered, false otherwise.")
    );
        
    public final static FunctionSignature FNS_DEREGISTER_RESOURCE_FUNCTION = new FunctionSignature(
        qnDeregisterResourceFunction,
        "Deregisters a resource function from the RestXQ Registry.",
        new SequenceType[]{
            PARAM_MODULE,
            PARAM_RESOURCE_FUNCTION,
        },
        new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ONE, "true if the function was deregistered, false otherwise.")
    );
    
    public RegistryFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
        final XmldbURI moduleUri = args[0].toJavaObject(XmldbURI.class);
        final ExistXqueryRegistry xqueryRegistry = ExistXqueryRegistry.getInstance();
        final RestXqServiceRegistry registry = RestXqServiceRegistryManager.getRegistry(getContext().getBroker().getBrokerPool());
                
        Sequence result = Sequence.EMPTY_SEQUENCE;
                
        try {
            if(isCalledAs(qnRegisterModule.getLocalPart())) {
                final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
                if(xqueryRegistry.isXquery(module)) {
                    try {
                        final List<RestXqService> resourceFunctions = xqueryRegistry.findServices(getContext().getBroker(), module);
                        xqueryRegistry.registerServices(getContext().getBroker(), resourceFunctions);
                        result = (NodeValue)org.exist.extensions.exquery.restxq.impl.xquery.RegistryFunctions.serializeRestXqServices(context.getDocumentBuilder(), resourceFunctions).getDocumentElement();
                    } catch(final ExQueryException exqe) {
                        LOG.warn(exqe.getMessage(), exqe);
                        result = Sequence.EMPTY_SEQUENCE;
                    }
                } else {
                   result = Sequence.EMPTY_SEQUENCE; 
                }
            } else if(isCalledAs(qnDeregisterModule.getLocalPart())) {
                final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
                if(xqueryRegistry.isXquery(module)) {                
                    final List<RestXqService> deregisteringServices = new ArrayList<RestXqService>();
                    for(final RestXqService service : registry) {
                        if(XmldbURI.create(service.getResourceFunction().getXQueryLocation()).equals(moduleUri)) {
                            deregisteringServices.add(service);
                        }
                    }
                    xqueryRegistry.deregisterServices(getContext().getBroker(), moduleUri);
                    result = (NodeValue)org.exist.extensions.exquery.restxq.impl.xquery.RegistryFunctions.serializeRestXqServices(context.getDocumentBuilder(), deregisteringServices).getDocumentElement();
                } else {
                   result = Sequence.EMPTY_SEQUENCE; 
                }
            } else if(isCalledAs(qnFindResourceFunctions.getLocalPart())) {
               final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
               if(xqueryRegistry.isXquery(module)) {                
                    try {
                        final List<RestXqService> resourceFunctions = xqueryRegistry.findServices(getContext().getBroker(), module);
                        xqueryRegistry.deregisterServices(getContext().getBroker(), moduleUri);
                        result = (NodeValue)org.exist.extensions.exquery.restxq.impl.xquery.RegistryFunctions.serializeRestXqServices(context.getDocumentBuilder(), resourceFunctions).getDocumentElement();
                    } catch(final ExQueryException exqe) {
                        LOG.warn(exqe.getMessage(), exqe);
                        result = Sequence.EMPTY_SEQUENCE;
                    }
                } else {
                   result = Sequence.EMPTY_SEQUENCE; 
                }
            } else if(isCalledAs(qnRegisterResourceFunction.getLocalPart())) {
               final String resourceFunctionIdentifier = args[1].getStringValue();
               final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
               if(xqueryRegistry.isXquery(module)) {
                   final SignatureDetail signatureDetail = extractSignatureDetail(resourceFunctionIdentifier);
                   if(signatureDetail != null) { 
                       try {
                         final RestXqService serviceToRegister = findService(xqueryRegistry.findServices(getContext().getBroker(), module).iterator(), signatureDetail);
                         if(serviceToRegister != null) {
                             xqueryRegistry.registerServices(context.getBroker(), Collections.singletonList(serviceToRegister));
                             result = BooleanValue.TRUE;
                         } else {
                             result = BooleanValue.FALSE;
                         }
                       } catch(final ExQueryException exqe) {
                           LOG.warn(exqe.getMessage(), exqe);
                           result = BooleanValue.FALSE;
                       }
                   } else {
                       result = BooleanValue.FALSE;
                   }

                } else {
                   result = Sequence.EMPTY_SEQUENCE; 
                }
            } else if(isCalledAs(qnDeregisterResourceFunction.getLocalPart())) {
                //TODO
                final String resourceFunctionIdentifier = args[1].getStringValue();
                final SignatureDetail signatureDetail = extractSignatureDetail(resourceFunctionIdentifier);
                if(signatureDetail != null) { 
                   final RestXqService serviceToDeregister = findService(xqueryRegistry.registered(context.getBroker()), signatureDetail);         
                   if(serviceToDeregister != null) {
                    xqueryRegistry.deregisterService(context.getBroker(), serviceToDeregister);
                    result = BooleanValue.TRUE;
                   } else {
                       result = BooleanValue.FALSE;
                   }
                } else {
                  result = BooleanValue.FALSE;  
                }
            }
            return result;
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde.getMessage(), pde);
        }
    }
    
    private RestXqService findService(final Iterator<RestXqService> services, final SignatureDetail signatureDetail) {
        RestXqService result = null;
        if(services != null) {
            while(services.hasNext()) {
                final RestXqService service = services.next();
                final org.exquery.xquery.FunctionSignature signature = service.getResourceFunction().getFunctionSignature();
                if(signature.getName().equals(signatureDetail.name) && signature.getArgumentCount() == signatureDetail.arity) {
                    result = service;
                    break;
                }
            }
        }
        return result;
    }
    
    private SignatureDetail extractSignatureDetail(final String resourceFunctionIdentifier) {
        SignatureDetail result = null;
        if(resourceFunctionIdentifier.indexOf('#') > -1) {
            final int arity = Integer.parseInt(resourceFunctionIdentifier.substring(resourceFunctionIdentifier.indexOf('#') + 1));
            final javax.xml.namespace.QName name;
            if(resourceFunctionIdentifier.startsWith("{")) {
                name = new javax.xml.namespace.QName(resourceFunctionIdentifier.substring(1, resourceFunctionIdentifier.indexOf('}')));
            } else {
                name = new javax.xml.namespace.QName(resourceFunctionIdentifier.substring(0, resourceFunctionIdentifier.indexOf('#')), resourceFunctionIdentifier.substring(resourceFunctionIdentifier.indexOf('}')));
            }
            result = new SignatureDetail(name, arity);
        }
        return result;
    }
    
    private class SignatureDetail {
        javax.xml.namespace.QName name; 
        int arity;
        
        public SignatureDetail(final javax.xml.namespace.QName name, final int arity) {
            this.name = name;
            this.arity = arity;
        }
    }
}