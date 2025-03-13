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
package org.exist.extensions.exquery.restxq.impl.xquery;


import java.util.ArrayList;
import java.util.List;
import org.exist.dom.QName;
import org.exist.extensions.exquery.restxq.impl.RestXqServiceRegistryManager;
import org.exist.dom.memtree.MemTreeBuilder;
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
import org.exquery.restxq.annotation.*;
import org.exquery.serialization.annotation.AbstractYesNoSerializationAnnotation;
import org.exquery.serialization.annotation.MethodAnnotation;
import org.exquery.serialization.annotation.SerializationAnnotation;
import org.exquery.xquery.Literal;
import org.exquery.xquery3.Annotation;
import org.w3c.dom.Document;
import org.xml.sax.helpers.AttributesImpl;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RegistryFunctions extends BasicFunction {
    
    private final static QName RESOURCE_FUNCTIONS = new QName("resource-functions", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    private final static QName RESOURCE_FUNCTION = new QName("resource-function", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    private final static QName ANNOTATIONS = new QName("annotations", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    private final static QName SEGMENT = new QName("segment", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    private final static QName INTERNET_MEDIA_TYPE = new QName("internet-media-type", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);

    private final static String XQUERY_URI = "xquery-uri";
    private final static QName RESOURCE_FUNCTION_IDENTITY = new QName("identity", RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX);
    private final static String NAMESPACE = "namespace";
    private final static String LOCAL_NAME = "local-name";
    private final static String ARITY = "arity";
    private final static String VALUE = "value";
    private final static String NAME = "name";
    private final static String ARGUMENT = "argument";
    private final static String DEFAULT_VALUE = "default-value";
    private final static String SPECIFICITY_METRIC = "specificity-metric";
    
    public final static FunctionSignature signatures[] = {
		
        new FunctionSignature(
            new QName(RESOURCE_FUNCTIONS.getLocalPart(), RestXqModule.NAMESPACE_URI, RestXqModule.PREFIX),
            "Gets a list of all the registered resource functions.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "The list of registered resource functions.")
        )
    };
    
    public RegistryFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
        final RestXqServiceRegistry registry = RestXqServiceRegistryManager.getRegistry(getContext().getBroker().getBrokerPool());
        context.pushDocumentContext();
        try {
            return (NodeValue) serializeRestXqServices(getContext().getDocumentBuilder(), registry).getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }

    /**
     * Serializes RESTXQ Services to an XML description
     *
     * @param builder The receiver for the serialization
     * @param services The services to describe
     *
     * @return The XML Document constructed from serializing the
     * services to the MemTreeBuilder
     */
    public static Document serializeRestXqServices(final MemTreeBuilder builder, final Iterable<RestXqService> services) {
        builder.startDocument();
        builder.startElement(RESOURCE_FUNCTIONS, null);
        
        for(final RestXqService service : services) {
            final ResourceFunction resourceFn = service.getResourceFunction();
            serializeResourceFunction(builder, resourceFn);
        }
        
        builder.endElement();
        builder.endDocument();

        return builder.getDocument();
    }

    /**
     * Serializes a RESTXQ Resource Function as an XML description
     *
     * @param builder The receiver for the serialization
     * @param resourceFn The resource function to describe
     */
    private static void serializeResourceFunction(final MemTreeBuilder builder, final ResourceFunction resourceFn) {
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

        //rest annotations
        builder.startElement(ANNOTATIONS, null);
        serializeAnnotations(builder, resourceFn);
        builder.endElement();

        builder.endElement();
    }

    private static void serializeAnnotations(final MemTreeBuilder builder, final ResourceFunction resourceFn) {
        final List<Annotation> annotations = new ArrayList<>();
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

        //path annotation
        if(resourceFn.getPathAnnotation() != null) {
            final PathAnnotation pathAnnotation = resourceFn.getPathAnnotation();
            final AttributesImpl attrs = new AttributesImpl();
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

        //parameter annotations
        for(final ParameterAnnotation parameterAnnotation : resourceFn.getParameterAnnotations()) {
            final Literal[] literals = parameterAnnotation.getLiterals();
            final AttributesImpl attrs = new AttributesImpl();
            attrs.addAttribute(null, NAME, "", "string", literals[0].getValue());
            attrs.addAttribute(null, ARGUMENT, "", "string", literals[1].getValue());
            if(literals.length == 3) {
                attrs.addAttribute(null, DEFAULT_VALUE, "", "string", literals[2].getValue());
            }
            builder.startElement(QName.fromJavaQName(parameterAnnotation.getName()), attrs);
            builder.endElement();
        }

        //serialization annotations
        for(final SerializationAnnotation serializationAnnotation : resourceFn.getSerializationAnnotations()) {
            serializeSerializationAnnotation(builder, serializationAnnotation);
        }
    }

    static void serializeSerializationAnnotation(final MemTreeBuilder builder, final SerializationAnnotation serializationAnnotation) {
        builder.startElement(QName.fromJavaQName(serializationAnnotation.getName()), null);

        switch (serializationAnnotation) {
            case AbstractYesNoSerializationAnnotation abstractYesNoSerializationAnnotation ->
                    builder.characters(abstractYesNoSerializationAnnotation.getStringValue());
            case org.exquery.serialization.annotation.MediaTypeAnnotation mediaTypeAnnotation ->
                    builder.characters(mediaTypeAnnotation.getValue());
            case org.exquery.serialization.annotation.EncodingAnnotation encodingAnnotation ->
                    builder.characters(encodingAnnotation.getValue());
            case MethodAnnotation methodAnnotation -> builder.characters(methodAnnotation.getMethod());
            default -> {
            }
        }

        //TODO further output: annotations as they are implemented

        builder.endElement();
    }
}