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


import java.util.*;

import org.exist.dom.memtree.MemTreeBuilder;
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
import org.exist.xquery.value.*;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RegistryFunctions extends BasicFunction {
    
    private final static QName qnFindResourceFunctions = new QName("find-resource-functions", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnRegisterModule = new QName("register-module", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnDeregisterModule = new QName("deregister-module", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnRegisterResourceFunction = new QName("register-resource-function", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnDeregisterResourceFunction = new QName("deregister-resource-function", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnInvalidModules = new QName("invalid-modules", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnMissingDependencies = new QName("missing-dependencies", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName qnDependencies = new QName("dependencies", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    
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

    public final static FunctionSignature FNS_INVALID_MODULES = new FunctionSignature(
            qnInvalidModules,
        "Gets a list of all the invalid XQuery modules discovered by RESTXQ in the process of discovering resource functions.",
        FunctionSignature.NO_ARGS,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "The list of invalid XQuery modules.")
    );

    public final static FunctionSignature FNS_MISSING_DEPENDENCIES = new FunctionSignature(
        qnMissingDependencies,
        "Gets a list of all the missing dependencies for XQuery modules discovered by RESTXQ in the process of discovering resource functions.",
        FunctionSignature.NO_ARGS,
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The list of missing dependencies.")
    );

    public final static FunctionSignature FNS_DEPENDENCIES = new FunctionSignature(
        qnDependencies,
        "Gets a list of all the dependencies of compiled XQuery modules discovered by RESTXQ in the process of discovering resource functions.",
        FunctionSignature.NO_ARGS,
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The list of dependencies.")
    );

    private final static QName MISSING_DEPENDENCIES = new QName("missing-dependencies", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName MISSING_DEPENDENCY = new QName("missing-dependency", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName REQUIRED_BY = new QName("required-by", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName DEPENDENCIES = new QName("dependencies", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName MODULE = new QName("module", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static QName REQUIRES = new QName("requires", ExistRestXqModule.NAMESPACE_URI, ExistRestXqModule.PREFIX);
    private final static String XQUERY_URI = "xquery-uri";

    public RegistryFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ExistXqueryRegistry xqueryRegistry = ExistXqueryRegistry.getInstance();
        final RestXqServiceRegistry registry = RestXqServiceRegistryManager.getRegistry(getContext().getBroker().getBrokerPool());
                
        Sequence result = Sequence.EMPTY_SEQUENCE;
                
        try {
            if(isCalledAs(qnRegisterModule.getLocalPart())) {
                final XmldbURI moduleUri = args[0].toJavaObject(XmldbURI.class);
                final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
                if(xqueryRegistry.isXquery(module)) {
                    try {
                        final List<RestXqService> resourceFunctions = xqueryRegistry.findServices(getContext().getBroker(), module);
                        xqueryRegistry.registerServices(getContext().getBroker(), resourceFunctions);
                        result = (NodeValue)org.exist.extensions.exquery.restxq.impl.xquery.RegistryFunctions.serializeRestXqServices(context.getDocumentBuilder(), resourceFunctions).getDocumentElement();
                    } catch(final ExQueryException e) {
                        LOG.warn(e.getMessage(), e);
                        result = Sequence.EMPTY_SEQUENCE;
                    }
                } else {
                   result = Sequence.EMPTY_SEQUENCE; 
                }
            } else if(isCalledAs(qnDeregisterModule.getLocalPart())) {
                final XmldbURI moduleUri = args[0].toJavaObject(XmldbURI.class);
                final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
                if(xqueryRegistry.isXquery(module)) {                
                    final List<RestXqService> deregisteringServices = new ArrayList<>();
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
               final XmldbURI moduleUri = args[0].toJavaObject(XmldbURI.class);
               final DocumentImpl module = getContext().getBroker().getResource(moduleUri, Permission.READ);
               if(xqueryRegistry.isXquery(module)) {                
                    try {
                        final List<RestXqService> resourceFunctions = xqueryRegistry.findServices(getContext().getBroker(), module);
                        xqueryRegistry.deregisterServices(getContext().getBroker(), moduleUri);
                        result = (NodeValue)org.exist.extensions.exquery.restxq.impl.xquery.RegistryFunctions.serializeRestXqServices(context.getDocumentBuilder(), resourceFunctions).getDocumentElement();
                    } catch(final ExQueryException e) {
                        LOG.warn(e.getMessage(), e);
                        result = Sequence.EMPTY_SEQUENCE;
                    }
                } else {
                   result = Sequence.EMPTY_SEQUENCE; 
                }
            } else if(isCalledAs(qnRegisterResourceFunction.getLocalPart())) {
               final XmldbURI moduleUri = args[0].toJavaObject(XmldbURI.class);
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
                       } catch(final ExQueryException e) {
                           LOG.warn(e.getMessage(), e);
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
            } else if(isCalledAs(qnInvalidModules.getLocalPart())) {
                result = serializeInvalidQueries(xqueryRegistry.getInvalidQueries());
            } else if(isCalledAs(qnMissingDependencies.getLocalPart())) {
                result = serializeMissingDependencies(xqueryRegistry.getMissingDependencies());
            } else if(isCalledAs(qnDependencies.getLocalPart())) {
                result = serializeDependenciesTree(xqueryRegistry.getDependenciesTree());
            }
            return result;
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, pde.getMessage(), pde);
        }
    }

    private Sequence serializeDependenciesTree(final Map<String, Set<String>> dependenciesTree) {
        final MemTreeBuilder builder = getContext().getDocumentBuilder();

        builder.startDocument();
        builder.startElement(DEPENDENCIES, null);

        for(final Map.Entry<String, Set<String>> dependencyTree : dependenciesTree.entrySet()) {
            serializeDependencyTree(builder, dependencyTree);
        }

        builder.endElement();
        builder.endDocument();

        return builder.getDocument();
    }

    private void serializeDependencyTree(final MemTreeBuilder builder, final Map.Entry<String, Set<String>> dependencyTree) {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute(null, XQUERY_URI, "", "string", dependencyTree.getKey());

        builder.startElement(MODULE, attrs);

        for(final String dependency : dependencyTree.getValue()) {
            attrs = new AttributesImpl();
            attrs.addAttribute(null, XQUERY_URI, "", "string", dependency);

            builder.startElement(REQUIRES, attrs);
            builder.endElement();
        }

        builder.endElement();
    }

    private Sequence serializeMissingDependencies(final Map<String, Set<String>> missingDependencies) {
        final MemTreeBuilder builder = getContext().getDocumentBuilder();

        builder.startDocument();
        builder.startElement(MISSING_DEPENDENCIES, null);

        for(final Map.Entry<String, Set<String>> missingDependency : missingDependencies.entrySet()) {
            serializeMissingDependency(builder, missingDependency);
        }

        builder.endElement();
        builder.endDocument();

        return builder.getDocument();
    }

    private void serializeMissingDependency(final MemTreeBuilder builder, final Map.Entry<String, Set<String>> missingDependency) {
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute(null, XQUERY_URI, "", "string", missingDependency.getKey());

        builder.startElement(MISSING_DEPENDENCY, attrs);

        for(final String dependent : missingDependency.getValue()) {
            attrs = new AttributesImpl();
            attrs.addAttribute(null, XQUERY_URI, "", "string", dependent);

            builder.startElement(REQUIRED_BY, attrs);
            builder.endElement();
        }

        builder.endElement();
    }

    private Sequence serializeInvalidQueries(final Set<String> invalidQueries) throws XPathException {
        final Sequence result = new ValueSequence();
        for(final String invalidQuery : invalidQueries) {
            result.add(new StringValue(invalidQuery));
        }
        return result;
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
        final javax.xml.namespace.QName name;
        final int arity;
        
        public SignatureDetail(final javax.xml.namespace.QName name, final int arity) {
            this.name = name;
            this.arity = arity;
        }
    }
}