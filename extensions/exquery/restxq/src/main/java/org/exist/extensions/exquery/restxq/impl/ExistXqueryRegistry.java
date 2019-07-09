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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ExistXqueryRegistry {

    //singleton
    private final static ExistXqueryRegistry instance = new ExistXqueryRegistry();
    private ExistXqueryRegistry() {
    }
    
    public static ExistXqueryRegistry getInstance() {
        return instance;
    }
    
    
    private final static Logger LOG = LogManager.getLogger(ExistXqueryRegistry.class);
    
    /**
     * Key is XQuery Module URI
     * Value is set of XQuery Module URIs on which the Module indicated by the Key depends on
     */
    private final static Map<String, Set<String>> dependenciesTree = new HashMap<>();

    /**
     * Returns a copy of the known dependency tree
     * @return copy of the known dependency tree
     */
    public Map<String, Set<String>> getDependenciesTree() {
        final Map<String, Set<String>> copy = new HashMap<>();
        for(final Map.Entry<String, Set<String>> dependencyTree : dependenciesTree.entrySet()) {
            copy.put(dependencyTree.getKey(), new HashSet<>(dependencyTree.getValue()));
        }
        return copy;
    }

    /**
     * Key is the missing Module URI
     * Value is the Set of XQuery Module URIs that require the missing Module indicated by the Key
     */
    private final static Map<String, Set<String>> missingDependencies = new HashMap<>();

    /**
     * Returns a copy of the current known missing dependencies
     * @return copy of the current known missing dependencies
     */
    public Map<String, Set<String>> getMissingDependencies() {
        final Map<String, Set<String>> copy = new HashMap<>();
        for(final Map.Entry<String, Set<String>> missingDependency : missingDependencies.entrySet()) {
            copy.put(missingDependency.getKey(), new HashSet<>(missingDependency.getValue()));
        }
        return copy;
    }

    /**
     * The list of XQuerys that could not be compiled
     * for reasons other than missing dependencies
     */
    private final static Set<String> invalidQueries = new HashSet<>();

    /**
     * Returns a copy of the current known invalid queries
     * @return a copy of the current known invalid queries
     */
    public Set<String> getInvalidQueries() {
        return new HashSet<>(invalidQueries);
    }

    public boolean isXquery(final DocumentImpl document) {
         return document instanceof BinaryDocument && document.getMetadata().getMimeType().equals(XQueryCompiler.XQUERY_MIME_TYPE);
    }
    
    public void registerServices(final DBBroker broker, final List<RestXqService> services) {
        getRegistry(broker).register(services);
    }
    
    public void deregisterServices(final DBBroker broker, final XmldbURI xqueryLocation) {
        
        getRegistry(broker).deregister(xqueryLocation.getURI());
        
        //find and remove services from modules that depend on this one
        for(final String dependant : getDependants(xqueryLocation)) {
            try {
                
                //TODO This null check is a temporary workaround
                //as a NPE in the URI class was reported by Wolf
                //where dependant was null. I can only imagine
                //that another thread interrupted and removed it
                //from the hashmap that it comes from.
                //its quite possible the use of synchronized around
                //the various maps in this class is not sufficient in scope
                //and we should move to some locks and operating over closures
                //on the maps.
                if(dependant != null) {
                    getRegistry(broker).deregister(new URI(dependant));

                    //record the now missing dependency
                    recordMissingDependency(xqueryLocation.toString(), XmldbURI.create(dependant));
                }
            } catch(final URISyntaxException e) {
                LOG.error(e.getMessage(), e);
            }
        }
        
        /*
         * update the missingDependencies??
         * Probably not needed as this will be done in find services
         */
    }
    
    public void deregisterService(final DBBroker broker, final RestXqService service) {
        
        getRegistry(broker).deregister(service);
        
        //TODO below is not needed as we are not removing the module just a single service
        //find and remove services from modules that depend on this one
        /*for(final String dependant : getDependants(xqueryLocation)) {
            try {
                
                //TODO This null check is a temporary workaround
                //as a NPE in the URI class was reported by Wolf
                //where dependant was null. I can only imagine
                //that another thread interrupted and removed it
                //from the hashmap that it comes from.
                //its quite possible the use of synchronized around
                //the various maps in this class is not sufficient in scope
                //and we should move to some locks and operating over closures
                //on the maps.
                if(dependant != null) {
                    getRegistry(broker).deregister(new URI(dependant));

                    //record the now missing dependency
                    recordMissingDependency(xqueryLocation.toString(), XmldbURI.create(dependant));
                }
            } catch(final URISyntaxException e) {
                LOG.error(e.getMessage(), e);
            }
        }*/
        
        /*
         * update the missingDependencies??
         * Probably not needed as this will be done in find services
         */
    }
    
    private Set<String> getDependants(final XmldbURI xqueryLocation) {
        final Set<String> dependants = new HashSet<>();
        
        //make a copy of the dependenciesTree into depTree
        final Map<String, Set<String>> depTree;
        synchronized(dependenciesTree) {
            depTree = new HashMap<>(dependenciesTree);
        }
        
        //find all modules that have a dependency on this one
        for(final Map.Entry<String, Set<String>> depTreeEntry : depTree.entrySet()) {
            for(final String dependency : depTreeEntry.getValue()) {
                if(dependency.equals(xqueryLocation.toString())) {
                    dependants.add(depTreeEntry.getKey());
                    continue; //TODO(AR) do we mean continue or break?
                }
            }
        }
        return dependants;
    }
    
    public Iterator<RestXqService> registered(final DBBroker broker) {
        return getRegistry(broker).iterator();
    }
    
    public List<RestXqService> findServices(final DBBroker broker, final DocumentImpl document) throws ExQueryException {
        
        try {
            final CompiledXQuery compiled = XQueryCompiler.compile(broker, document);

            /*
             * examine the compiled query, record all modules and modules of modules.
             * Keep a dependencies list so that we can act on it if a module is deleted.
             */ 
            final Map<String, Set<String>> queryDependenciesTree = XQueryInspector.getDependencies(compiled);
            recordQueryDependenciesTree(queryDependenciesTree);

            /*
             * A compiled query may be a missing dependency for another query
             * so reexamine queries with missing dependencies
             */
            reexamineModulesWithResolvedDependencies(broker, document.getURI().toString());

            /*
             * remove any potentially re-compiled query from the
             * invalid queries list
             */ 
            removeInvalidQuery(document.getURI());

            return XQueryInspector.findServices(compiled);
        } catch(final RestXqServiceCompilationException e) {

            //if there was a missing dependency then record it
            final MissingModuleHint missingModuleHint = extractMissingModuleHint(e);
            if(missingModuleHint != null) {
                
                if(missingModuleHint.dependantModule == null) {
                    recordMissingDependency(missingModuleHint.moduleHint, document.getURI());
                } else {
                    //avoids wrong missing dependency dependant being recorded for a complex module tree
                    try {
                        recordMissingDependency(missingModuleHint.dependantModule, document.getURI());
                        recordMissingDependency(missingModuleHint.moduleHint, XmldbURI.xmldbUriFor(missingModuleHint.dependantModule));
                    } catch(final URISyntaxException use) {
                        recordInvalidQuery(document.getURI());
                        LOG.error("XQuery '" + document.getURI() + "' could not be compiled! " + e.getMessage());
                    }
                }
            } else {
                recordInvalidQuery(document.getURI());
                LOG.error("XQuery '" + document.getURI() + "' could not be compiled! " + e.getMessage());
            }

            /*
             * This may be the recompilation of a query
             * so we should unregister any of its missing
             * services. Luckily this is taken care of in
             * the before{EVENT} trigger functions
             */
        }
        
        return new ArrayList<>();
    }
    
   
    /**
     * Gets the modules that have a missing dependency
     * on the module indicated by compiledModuleURI
     * and attempts to re-compile them and register their
     * services
     */
    private void reexamineModulesWithResolvedDependencies(final DBBroker broker, final String compiledModuleUri) {
        
        final Set<String> dependants;
        synchronized(missingDependencies) {
            final Set<String> deps = missingDependencies.get(compiledModuleUri);
            if(deps != null) {
                dependants = new HashSet<>(deps);
            } else {
                dependants = new HashSet<>();
            }
        }
        
        for(final String dependant : dependants) {
            
            try {
                
                final DocumentImpl dependantModule = broker.getResource(XmldbURI.create(dependant), Permission.READ);
                
                /**
                 * This null check is needed, as a dependency module may have been renamed,
                 * and so is no longer accessible under its old URI.
                 *
                 * However if its dependant module (compiledModuleUri) compiles
                 * (which it must have for this function to be invoked)
                 * then we can assume that the dependant module references the new
                 * module dependency (in the case of a module move/rename)
                 * or the dependency has been removed
                 */
                if(dependantModule != null) {
                    LOG.info("Missing dependency '" + compiledModuleUri +"' has been added to the database, re-examining '" + dependant + "'...");
                    
                    final List<RestXqService> services = findServices(broker, dependantModule);
                    LOG.info("Discovered " + services.size() + " resource functions for " + dependant);
                    registerServices(broker, services);
                } else {
                    LOG.info("Dependant '" + compiledModuleUri + "' has been resolved. Dependency on: " + dependant + " was removed");

                    //we need to remove dependant from the dependenciesTree of dependant
                    removeDependency(dependant, compiledModuleUri);
                }
            } catch(final PermissionDeniedException | ExQueryException pde) {
                LOG.error(pde.getMessage(), pde);
            }

            //remove the resolve dependencies from the missing dependencies
            removeMissingDependency(compiledModuleUri, dependant);
        }
    }
    
    private void removeMissingDependency(final String dependency, final String dependant) {
        synchronized(missingDependencies) {
            final Set<String> missingDependants = missingDependencies.get(dependency);
            missingDependants.remove(dependant);
            if(missingDependants.isEmpty()) {
                missingDependencies.remove(dependency);
            }
        }
    }
    
    private void recordQueryDependenciesTree(final Map<String, Set<String>> queryDependenciesTree) {
        synchronized(dependenciesTree) {
            //Its not a merge its an overwrite!
            dependenciesTree.putAll(queryDependenciesTree);
        }
    }
    
    private void removeInvalidQuery(final XmldbURI xqueryUri) {
        synchronized(invalidQueries) {
            invalidQueries.remove(xqueryUri.toString());
        }
    }
    
    private void recordInvalidQuery(final XmldbURI xqueryUri) {
        synchronized(invalidQueries) {
            invalidQueries.add(xqueryUri.toString());
        }
    }

    private static class MissingModuleHint {
        public String moduleHint = null;
        public String dependantModule = null;
    }
    
    private MissingModuleHint extractMissingModuleHint(final RestXqServiceCompilationException e) {
        
        MissingModuleHint missingModuleHint = null;
        
        if(e.getCause() instanceof XPathException) {
            final XPathException xpe = (XPathException)e.getCause();
            if(xpe.getErrorCode() == ErrorCodes.XQST0059) {
                final Sequence errorVals = xpe.getErrorVal();
                
                if(errorVals != null && errorVals.getItemCount() > 0){
                    
                    final Item errorVal1 = errorVals.itemAt(0);
                    if(errorVal1 instanceof StringValue) {
                        missingModuleHint = new MissingModuleHint();
                        missingModuleHint.moduleHint = ((StringValue)errorVal1).getStringValue();
                    }
                    
                    if(errorVals.getItemCount() == 2) {
                        final Item errorVal2 = errorVals.itemAt(1);
                        if(errorVal2 instanceof StringValue) {
                            if(missingModuleHint == null) {
                                missingModuleHint = new MissingModuleHint();
                            }
                            
                            final String dependantModuleUri = ((StringValue)errorVal2).getStringValue();
                            
                            //path will be of xmldb:exist:///db/a/c/1.xqm form so change it to /db/a/c/1.xqm form
                            missingModuleHint.dependantModule = makeDbAbsolutePath(dependantModuleUri);
                        }
                    }
                }
            }
        }
        
        return missingModuleHint;
    }
    
    private void recordMissingDependency(final String moduleHint, final XmldbURI xqueryUri) {
        final String moduleUri = getAbsoluteModuleHint(moduleHint, xqueryUri);
        
        synchronized(missingDependencies) {
            final Set<String> dependants;
            if(missingDependencies.containsKey(moduleUri)) {
                dependants = missingDependencies.get(moduleUri);
            } else {
                dependants = new HashSet<>();
            }
            
            dependants.add(xqueryUri.toString());
            
            missingDependencies.put(moduleUri, dependants);
        }
        
        LOG.warn("Module '" + xqueryUri + "' has a missing dependency on '" + moduleUri + "'. Will re-examine if the missing module is added.");
    }
    
    private void removeDependency(final String dependant, final String dependency) {
        synchronized(dependenciesTree) {
            final Set<String> dependencies = dependenciesTree.get(dependant);
            if(dependencies != null) {
                dependencies.remove(dependency);
                if(dependencies.isEmpty()) {
                    dependenciesTree.remove(dependant);
                }
            }
        }
    }
    
    protected String getAbsoluteModuleHint(final String moduleHint, final XmldbURI xqueryUri) {
        if(moduleHint.startsWith(XmldbURI.ROOT_COLLECTION)) {
            //absolute simple path
            return moduleHint;
        } else if(moduleHint.startsWith(XmldbURI.EMBEDDED_SERVER_URI.toString())) {
            return moduleHint.replace(XmldbURI.EMBEDDED_SERVER_URI.toString(), "");
        } else if(moduleHint.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX)) {
            return moduleHint.replace(XmldbURI.EMBEDDED_SERVER_URI_PREFIX, "");
        } else {
            
            //relative to the xqueryUri
            final XmldbURI xqueryPath = xqueryUri.removeLastSegment();
            
            return xqueryPath.append(moduleHint).toString();
        }
    }
    
    /**
     * Converts an xmldb:exist:// path to an Absolute DB path
     * 
     * e.g. path  xmldb:exist:///db/a/c/1.xqm form will to /db/a/c/1.xqm form
     */
    private String makeDbAbsolutePath(String dependantModuleUri) {
        dependantModuleUri = dependantModuleUri.replace(XmldbURI.EMBEDDED_SERVER_URI.toString(), "");
        dependantModuleUri = dependantModuleUri.replace(XmldbURI.EMBEDDED_SERVER_URI_PREFIX, "");
        if(!dependantModuleUri.isEmpty() && !dependantModuleUri.startsWith("/")) {
            dependantModuleUri = dependantModuleUri.substring(dependantModuleUri.indexOf("/"));
        }
        return dependantModuleUri;
    }
    
    private RestXqServiceRegistry getRegistry(final DBBroker broker) {
        return RestXqServiceRegistryManager.getRegistry(broker.getBrokerPool());
    }
}
