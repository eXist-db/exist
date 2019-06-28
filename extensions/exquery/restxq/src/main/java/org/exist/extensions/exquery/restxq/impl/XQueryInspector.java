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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.exist.extensions.exquery.restxq.impl.adapters.AnnotationAdapter;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.xquery.Annotation;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.Module;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XQueryContext;
import org.exquery.ExQueryException;
import org.exquery.annotation.AnnotationException;
import org.exquery.restxq.ResourceFunction;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.impl.ResourceFunctionFactory;
import org.exquery.restxq.impl.annotation.RestAnnotationFactory;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
class XQueryInspector {
    
    public static List<RestXqService> findServices(final CompiledXQuery compiled) throws ExQueryException {
        
        final List<RestXqService> services = new ArrayList<>();
        
        try {
            //look at each function
            final Iterator<UserDefinedFunction> itFunctions = compiled.getContext().localFunctions();

            final Set<URI> xqueryLocations = new HashSet<>();
            while(itFunctions.hasNext()) {
                final UserDefinedFunction function = itFunctions.next();
                final Annotation annotations[] = function.getSignature().getAnnotations();

                Set<org.exquery.xquery3.Annotation> functionRestAnnotations = null;

                //process the function annotations
                for(final Annotation annotation : annotations) {
                    if(RestAnnotationFactory.isRestXqAnnotation(annotation.getName().toJavaQName())) {
                        final org.exquery.xquery3.Annotation restAnnotation = RestAnnotationFactory.getAnnotation(new AnnotationAdapter(annotation));
                        if(functionRestAnnotations == null) {
                            functionRestAnnotations = new HashSet<>();
                        }
                        functionRestAnnotations.add(restAnnotation);
                    }
                }

                if(functionRestAnnotations != null) {
                    final ResourceFunction resourceFunction = ResourceFunctionFactory.create(new URI(compiled.getSource().path()), functionRestAnnotations);
                    final RestXqService service = new RestXqServiceImpl(resourceFunction, compiled.getContext().getBroker().getBrokerPool());

                    //record the xquerylocation
                    xqueryLocations.add(resourceFunction.getXQueryLocation());
                    
                    //add the service to the list of services for this query
                    services.add(service);
                }
            }
            
            for(final URI xqueryLocation : xqueryLocations) {
                //add service location and compiled query to the cache
                RestXqServiceCompiledXQueryCacheImpl.getInstance().returnCompiledQuery(xqueryLocation, compiled);
            }
            
        } catch(final URISyntaxException | AnnotationException use) {
            throw new ExQueryException(use.getMessage(), use);
        }

        return services;
    }
    
    public static Map<String, Set<String>> getDependencies(final CompiledXQuery compiled) {
        final Map<String, Set<String>> dependencies = new HashMap<>();
        getDependencies(compiled.getContext(), dependencies);
        return dependencies;
    }
    
    private static void getDependencies(final XQueryContext xqyCtx, final Map<String, Set<String>> dependencies) {
        
        final String xqueryUri = getDbUri(xqyCtx.getSource());
        Set<String> depSet = dependencies.get(xqueryUri);
        if(depSet == null) {
        
            final Iterator<Module> itModule = xqyCtx.getModules();
            while(itModule.hasNext()) {
                final Module module = itModule.next();
                if(module instanceof ExternalModule) {
                    final ExternalModule extModule = (ExternalModule)module;
                    final Source source = extModule.getSource();
                    if(source instanceof DBSource) {
                        final String moduleUri = getDbUri(source);
                        if(depSet == null) {
                            depSet = new HashSet<>();
                        }
                        depSet.add(moduleUri);
                        
                        /*
                         * must merge map here as recursive function
                         * can cause problems with recursive
                         * module imports m1 -> m2 -> m2 -> m1
                         */
                        dependencies.put(xqueryUri, depSet);
                    }

                    getDependencies(extModule.getContext(), dependencies);
                }
            }
        }
    }
    
    private static String getDbUri(final Source source) {
        if(source != null && source instanceof DBSource) {
            return source.getKey().toString();
        } else {
            return null;
        }
    }
}
