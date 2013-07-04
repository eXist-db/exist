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


import java.util.ArrayList;
import java.util.List;
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
import org.exquery.restxq.ResourceFunction;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;
import org.exquery.restxq.annotation.ConsumesAnnotation;
import org.exquery.restxq.annotation.ParameterAnnotation;
import org.exquery.restxq.annotation.PathAnnotation;
import org.exquery.restxq.annotation.ProducesAnnotation;
import org.exquery.serialization.annotation.SerializationAnnotation;
import org.exquery.xquery.Literal;
import org.exquery.xquery3.Annotation;
import org.w3c.dom.Document;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RegistryFunctions extends BasicFunction {
    
    public final static QName RESOURCE_FUNCTIONS = new QName("resource-functions", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static QName RESOURCE_FUNCTION = new QName("resource-function", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static QName ANNOTATIONS = new QName("annotations", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static QName SEGMENT = new QName("segment", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static QName INTERNET_MEDIA_TYPE = new QName("internet-media-type", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    
    public final static String XQUERY_URI = "xquery-uri";
    public final static QName RESOURCE_FUNCTION_IDENTITY = new QName("identity", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    public final static String NAMESPACE = "namespace";
    public final static String LOCAL_NAME = "local-name";
    public final static String ARITY = "arity";
    public final static String VALUE = "value";
    public final static String NAME = "name";
    public final static String ARGUMENT = "argument";
    public final static String DEFAULT_VALUE = "default-value";
    public final static String SPECIFICITY_METRIC = "specificity-metric";
    
    public final static FunctionSignature signatures[] = {
		
        new FunctionSignature(
            new QName(RESOURCE_FUNCTIONS.getLocalName(), RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX),
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
        return (NodeValue)serializeRestXqServices(getContext().getDocumentBuilder(), registry).getDocumentElement();
    }
    
    public static Document serializeRestXqServices(final MemTreeBuilder builder, final Iterable<RestXqService> services) {
        builder.startDocument();
        builder.startElement(RESOURCE_FUNCTIONS, null);
        
        for(final RestXqService service : services) {
            
            final ResourceFunction resourceFn = service.getResourceFunction();
            
            AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(null, XQUERY_URI, "", "string", resourceFn.getXQueryLocation().toString());
            builder.startElement(RESOURCE_FUNCTION, attrs);
            
            //identity
            attrs = new AttributesImpl();
            attrs.addAttribute(null, NAMESPACE, "", "string", resourceFn.getFunctionSignature().getName().getNamespaceURI());
            attrs.addAttribute(null, LOCAL_NAME, "", "string", resourceFn.getFunctionSignature().getName().getLocalPart());
            attrs.addAttribute(null, ARITY, "", "int", Integer.toString(resourceFn.getFunctionSignature().getArgumentCount()));
            builder.startElement(RESOURCE_FUNCTION_IDENTITY, attrs);
            builder.endElement();
            
            //annotations
            builder.startElement(ANNOTATIONS, null);
            final List<Annotation> annotations = new ArrayList<Annotation>();
            annotations.addAll(resourceFn.getHttpMethodAnnotations());
            annotations.addAll(resourceFn.getConsumesAnnotations());
            annotations.addAll(resourceFn.getProducesAnnotations());
            
            for(final Annotation annotation : annotations) {
                builder.startElement(QName.fromJavaQName(annotation.getName()), null);
                
                final Literal literals[] =  annotation.getLiterals();
                if(literals != null) {
                    for(final Literal literal : literals) {
                        if(annotation instanceof ConsumesAnnotation || annotation instanceof ProducesAnnotation) {
                            builder.startElement(INTERNET_MEDIA_TYPE, null);
                            builder.characters(literal.getValue());
                            builder.endElement();
                        }
                    }
                }
                builder.endElement();
            }
            
            if(resourceFn.getPathAnnotation() != null) {
                final PathAnnotation pathAnnotation = resourceFn.getPathAnnotation();
                attrs = new AttributesImpl();
                attrs.addAttribute(null, SPECIFICITY_METRIC, "", "string", Long.toString(pathAnnotation.getPathSpecificityMetric()));
                builder.startElement(QName.fromJavaQName(pathAnnotation.getName()), attrs);
                final String[] segments = pathAnnotation.getLiterals()[0].getValue().split("/");
                for(final String segment : segments) {
                    if(!segment.isEmpty()) {
                        builder.startElement(SEGMENT, null);
                        builder.characters(segment);
                        builder.endElement();
                    }
                }
                builder.endElement();
            }
                
            for(final ParameterAnnotation parameterAnnotation : resourceFn.getParameterAnnotations()) {
                final Literal[] literals = parameterAnnotation.getLiterals();
                attrs = new AttributesImpl();
                attrs.addAttribute(null, NAME, "", "string", literals[0].getValue());
                attrs.addAttribute(null, ARGUMENT, "", "string", literals[1].getValue());
                if(literals.length == 3) {
                    attrs.addAttribute(null, DEFAULT_VALUE, "", "string", literals[2].getValue());
                }
                builder.startElement(QName.fromJavaQName(parameterAnnotation.getName()), attrs);
                builder.endElement();
            }
            
            for(final SerializationAnnotation serializationAnnotation : resourceFn.getSerializationAnnotations()) {
                builder.startElement(QName.fromJavaQName(serializationAnnotation.getName()), attrs);
                
                //TODO add parameters for Serialization Annotations
                
                builder.endElement();
            }
            builder.endElement();
            
            builder.endElement();
        }
        
        builder.endElement();
        builder.endDocument();
        
        
        return builder.getDocument();
    }
}