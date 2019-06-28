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
package org.exist.extensions.exquery.restxq.impl;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.extensions.exquery.restxq.RestXqServiceCompiledXQueryCache;
import org.exist.extensions.exquery.restxq.impl.adapters.SequenceAdapter;
import org.exist.extensions.exquery.restxq.impl.adapters.TypeAdapter;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.security.EffectiveSubject;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ProcessMonitor;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.TimeValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exquery.http.HttpRequest;
import org.exquery.restxq.Namespace;
import org.exquery.restxq.ResourceFunction;
import org.exquery.restxq.ResourceFunctionExecuter;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.xquery.Sequence;
import org.exquery.xquery.TypedArgumentValue;
import org.exquery.xquery.TypedValue;
import org.exquery.xquery3.FunctionSignature;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ResourceFunctionExecutorImpl implements ResourceFunctionExecuter {
    
    private final static Logger LOG = LogManager.getLogger(ResourceFunctionExecutorImpl.class);
    
    public final static QName XQ_VAR_BASE_URI = new QName("base-uri", Namespace.ANNOTATION_NS);
    public final static QName XQ_VAR_URI = new QName("uri", Namespace.ANNOTATION_NS);
    
    //TODO generalise with RequestModule
    private final static String EXQ_REQUEST_ATTR = "exquery-request";
    
    private final BrokerPool brokerPool;
    private final String uri;
    private final String baseUri;
    

    public ResourceFunctionExecutorImpl(final BrokerPool brokerPool, final String baseUri, final String uri) {
        this.brokerPool = brokerPool;
        this.baseUri = baseUri;
        this.uri = uri;
    }

    private BrokerPool getBrokerPool() {
        return brokerPool;
    }
    
    @Override
    public Sequence execute(final ResourceFunction resourceFunction, final Iterable<TypedArgumentValue> arguments, final HttpRequest request) throws RestXqServiceException {

        final RestXqServiceCompiledXQueryCache cache = RestXqServiceCompiledXQueryCacheImpl.getInstance();

        CompiledXQuery xquery = null;
        ProcessMonitor processMonitor = null;

        try(final DBBroker broker = getBrokerPool().getBroker()) {
            //ensure we can execute the function before going any further
            checkSecurity(broker, resourceFunction.getXQueryLocation());
            
            //get a compiled query service from the cache
            xquery = cache.getCompiledQuery(broker, resourceFunction.getXQueryLocation());

            //find the function that we will execute
            final UserDefinedFunction fn = findFunction(xquery, resourceFunction.getFunctionSignature());
            
            final XQueryContext xqueryContext = xquery.getContext();
            
            //set the request object - can later be used by the EXQuery Request Module
            xqueryContext.setAttribute(EXQ_REQUEST_ATTR, request);
            
            //TODO this is a workaround?
            declareVariables(xqueryContext);
            
            //START workaround: evaluate global variables in modules, as they are reset by XQueryContext.reset()
            final Expression rootExpr = xqueryContext.getRootExpression();
            for(int i = 0; i < rootExpr.getSubExpressionCount(); i++) {
                final Expression subExpr = rootExpr.getSubExpression(i);
                if(subExpr instanceof VariableDeclaration) {
                    subExpr.eval(null);
                }
            }
            //END workaround
            
            //setup monitoring
            processMonitor = broker.getBrokerPool().getProcessMonitor();
            xqueryContext.getProfiler().traceQueryStart();
            processMonitor.queryStarted(xqueryContext.getWatchDog());
            
            //create a function call
            try (final FunctionReference fnRef = new FunctionReference(new FunctionCall(xqueryContext, fn))) {

                //convert the arguments
                final org.exist.xquery.value.Sequence[] fnArgs = convertToExistFunctionArguments(xqueryContext, fn, arguments);

                //execute the function call
                fnRef.analyze(new AnalyzeContextInfo());

                //if setUid/setGid, determine the effectiveSubject to use for execution
                final Optional<EffectiveSubject> effectiveSubject = getEffectiveSubject(xquery);

                try {
                    effectiveSubject.ifPresent(broker::pushSubject);  //switch to effective user if setUid/setGid
                    final org.exist.xquery.value.Sequence result = fnRef.evalFunction(null, null, fnArgs);

                    // copy for closure
                    final CompiledXQuery xquery1 = xquery;

                    // return a sequence adapter which returns the query when it is finished with the results
                    return new SequenceAdapter(result, () -> {
                        if (xquery1 != null) {
                            //return the compiled query to the pool
                            cache.returnCompiledQuery(resourceFunction.getXQueryLocation(), xquery1);
                        }
                    });
                } finally {
                    //switch back from effective user if setUid/setGid
                    if (effectiveSubject.isPresent()) {
                        broker.popSubject();
                    }
                }
            }

        } catch(final URISyntaxException | EXistException | XPathException | PermissionDeniedException use) {

            // if an error occurred we should return the compiled query
            if(xquery != null) {
                //return the compiled query to the pool
                cache.returnCompiledQuery(resourceFunction.getXQueryLocation(), xquery);
            }

            throw new RestXqServiceException(use.getMessage(), use);
        } finally {
            //clear down monitoring
            if(processMonitor != null) {
                xquery.getContext().getProfiler().traceQueryEnd(xquery.getContext());
                processMonitor.queryCompleted(xquery.getContext().getWatchDog());
            }
        }
    }

    /**
     * If the compiled xquery is setUid and/or setGid
     * we return the EffectiveSubject that should be used
     * for execution
     *
     * @param xquery The XQuery to determine the effective subject for
     * @return Maybe an effective subject or empty if there is no setUid or setGid bits
     */
    private Optional<EffectiveSubject> getEffectiveSubject(final CompiledXQuery xquery) {
        final Optional<EffectiveSubject> effectiveSubject;

        final Source src = xquery.getContext().getSource();
        if(src instanceof DBSource) {
            final DBSource dbSrc = (DBSource)src;
            final Permission perm = dbSrc.getPermissions();

            if(perm.isSetUid()) {
                if(perm.isSetGid()) {
                    //setUid and SetGid
                    effectiveSubject = Optional.of(new EffectiveSubject(perm.getOwner(), perm.getGroup()));
                } else {
                    //just setUid
                    effectiveSubject = Optional.of(new EffectiveSubject(perm.getOwner()));
                }
            } else if(perm.isSetGid()) {
                //just setGid, so we use the current user as the effective user
                effectiveSubject = Optional.of(new EffectiveSubject(xquery.getContext().getBroker().getCurrentSubject(), perm.getGroup()));
            } else {
                effectiveSubject = Optional.empty();
            }
        } else {
            effectiveSubject = Optional.empty();
        }

        return effectiveSubject;
    }

    private void declareVariables(final XQueryContext xqueryContext) throws XPathException {
        xqueryContext.declareVariable(XQ_VAR_BASE_URI, baseUri);
        xqueryContext.declareVariable(XQ_VAR_URI, uri);
    }
    
    /**
     * Ensures that the xqueryLocation has READ and EXECUTE access
     * 
     * @param broker The current broker
     * @param xqueryLocation The xquery to check permissions for
     * 
     * @throws URISyntaxException if the xqueryLocation cannot be parsed
     * @throws PermissionDeniedException if there is not READ and EXECUTE access on the xqueryLocation for the current user
     */
    private void checkSecurity(final DBBroker broker, final URI xqueryLocation) throws URISyntaxException, PermissionDeniedException {
        broker.getResource(XmldbURI.xmldbUriFor(xqueryLocation), Permission.READ | Permission.EXECUTE);
    }
    
    /**
     * Lookup a Function in an XQuery given a Function Signature
     * 
     * @param xquery The XQuery to interrogate
     * @param functionSignature The Function Signature to use to match a Function
     * 
     * @return The Function from the XQuery matching the Function Signature
     */
    private UserDefinedFunction findFunction(final CompiledXQuery xquery, final FunctionSignature functionSignature) throws XPathException {
        final QName fnName = QName.fromJavaQName(functionSignature.getName());
        final int arity = functionSignature.getArgumentCount();      
        return xquery.getContext().resolveFunction(fnName, arity);
    }

    /**
     * Creates converts function arguments from EXQuery to eXist-db types
     * 
     * @param xqueryContext The XQuery Context of the XQuery containing the Function Call
     * @param fn The Function in the XQuery to create a Function Call for
     * @param arguments The arguments to be passed to the Function when its invoked
     * 
     * @return The arguments ready to pass to the Function Call when it is invoked
     */
    private org.exist.xquery.value.Sequence[] convertToExistFunctionArguments(final XQueryContext xqueryContext, final UserDefinedFunction fn, final Iterable<TypedArgumentValue> arguments) throws XPathException, RestXqServiceException {
        
        final List<org.exist.xquery.value.Sequence> fnArgs = new ArrayList<>();
        
        for(final SequenceType argumentType : fn.getSignature().getArgumentTypes()) {
            
            final FunctionParameterSequenceType fnParameter = (FunctionParameterSequenceType)argumentType;
            org.exist.xquery.value.Sequence fnArg = null;
            boolean found = false;
            for(final TypedArgumentValue argument : arguments) {
                final String argumentName = argument.getArgumentName();
                if(argumentName != null && argumentName.equals(fnParameter.getAttributeName())) {
                    
                    fnArg = convertToExistSequence(xqueryContext, argument, fnParameter.getPrimaryType());
                    
                    found = true;
                    break;
                }
            }
            
            if(!found) {
                //value is not always provided, e.g. by PathAnnotation, so use empty sequence

                //TODO do we need to check the cardinality of the receiving arg to make sure it permits ZERO?
                //argumentType.getCardinality();
        
                //create the empty sequence
                fnArg = org.exist.xquery.value.Sequence.EMPTY_SEQUENCE;
            }
            
            fnArgs.add(fnArg);
        }
        
        return fnArgs.toArray(new org.exist.xquery.value.Sequence[fnArgs.size()]);
    }

    
    //TODO this needs to be abstracted into EXQuery library / or not, see the TODOs below
    private <X> TypedValue<X> convertToType(final XQueryContext xqueryContext, final String argumentName, final TypedValue typedValue, final org.exquery.xquery.Type destinationType, final Class<X> underlyingDestinationClass) throws RestXqServiceException {
        
        //TODO consider changing Types that can be used as <T> to TypedValue to a set of interfaces for XDM types that
        //require absolute minimal implementation, and we provide some default or abstract implementations if possible
        
        final Item convertedValue;
        try {
            final int existDestinationType = TypeAdapter.toExistType(destinationType);
            
            final Item value;
            
            //TODO This type system is a complete mess:
            //EXQuery XDM should not have any concrete types, just interfaces
            //some of the abstract code in EXQuery needs to be able to instantiate types.
            //Consider a factory or java.util.ServiceLoader pattern
            if(typedValue instanceof org.exquery.xdm.type.StringTypedValue) {
                value = new StringValue(((org.exquery.xdm.type.StringTypedValue)typedValue).getValue());
            } else if(typedValue instanceof org.exquery.xdm.type.Base64BinaryTypedValue) {
                value = BinaryValueFromInputStream.getInstance(xqueryContext, new Base64BinaryValueType(), ((org.exquery.xdm.type.Base64BinaryTypedValue)typedValue).getValue());
            } else {
                value = (Item)typedValue.getValue();
            }

            if(existDestinationType == value.getType()) {
                convertedValue = value;
            } else if(value instanceof AtomicValue) {
                convertedValue = value.convertTo(existDestinationType);
            } else {
                LOG.warn("Could not convert parameter '" + argumentName + "' from '" + typedValue.getType().name() + "' to '" + destinationType.name() + "'.");
                convertedValue = value;
            }
            
        } catch(final XPathException xpe) {
            //TODO define an ErrorCode
            throw new RestXqServiceException("TODO need to implement error code for problem with parameter conversion!: " +  xpe.getMessage(), xpe);
        }
            
        return new TypedValue<X>() {

            @Override
            public org.exquery.xquery.Type getType() {
                //return destinationType;
                return TypeAdapter.toExQueryType(convertedValue.getType());
            }

            @Override
            public X getValue() {
                return (X)convertedValue;
            }
        };
    }
    
    private org.exist.xquery.value.Sequence convertToExistSequence(final XQueryContext xqueryContext, final TypedArgumentValue argument, final int fnParameterType) throws RestXqServiceException, XPathException  {
        final org.exist.xquery.value.Sequence sequence = new ValueSequence();
        
        for(final TypedValue value : (Sequence<Object>)argument.getTypedValue()) {
            
            final org.exquery.xquery.Type destinationType = TypeAdapter.toExQueryType(fnParameterType);
            
            final Class destinationClass;
            
            switch(fnParameterType) {
                
                case Type.ITEM:
                    destinationClass = Item.class;
                    break;
                    
                case Type.DOCUMENT:
                    destinationClass = DocumentImpl.class;  //TODO test this
                    break;
                
                case Type.STRING:
                    destinationClass = StringValue.class;
                    break;
                    
                case Type.INT:
                case Type.INTEGER:
                    destinationClass = IntegerValue.class;
                    break;
                    
                case Type.FLOAT:
                    destinationClass = FloatValue.class;
                    break;
                    
                case Type.DOUBLE:
                    destinationClass = DoubleValue.class;
                    break;    
                
                case Type.DECIMAL:
                    destinationClass = DecimalValue.class;
                    break; 
                
                case Type.DATE:
                    destinationClass = DateValue.class;
                    break;
                    
                case Type.DATE_TIME:
                    destinationClass = DateTimeValue.class;
                    break;
                    
                case Type.TIME:
                    destinationClass = TimeValue.class;
                    break;
                    
                case Type.QNAME:
                    destinationClass = QNameValue.class;
                    break;
                
                case Type.ANY_URI:
                    destinationClass = AnyURIValue.class;
                    break;
                
                case Type.BOOLEAN:
                    destinationClass = BooleanValue.class;
                    break;
                
                default:
                    destinationClass = Item.class;
            }
            
            final TypedValue<? extends Item> val = convertToType(xqueryContext, argument.getArgumentName(), value, destinationType, destinationClass);
            
            sequence.add(val.getValue());
        }
        
        return sequence;
    }
 
    public class DocumentImplExpressionAdapter extends AbstractExpression {
        private final DocumentImpl doc;

        public DocumentImplExpressionAdapter(final XQueryContext context, final DocumentImpl doc) {
            super(context);
            this.doc = doc;
        }

        @Override
        public org.exist.xquery.value.Sequence eval(final org.exist.xquery.value.Sequence contextSequence, final Item contextItem) throws XPathException {
            return doc;
        }

        @Override
        public int returnsType() {
            return Type.DOCUMENT;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
        }
    }
}