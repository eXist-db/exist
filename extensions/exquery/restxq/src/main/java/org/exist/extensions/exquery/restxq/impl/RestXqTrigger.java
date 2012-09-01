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
import java.util.*;
import java.util.Map.Entry;
import org.apache.log4j.Logger;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
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
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestXqTrigger extends FilteringTrigger {
    
    protected final static Logger LOG = Logger.getLogger(RestXqTrigger.class);
    
    /**
     * Key is XQuery Module URI
     * Value is set of XQuery Module URIs on which the Module indicated by the Key depends on
     */
    final static Map<String, Set<String>> dependenciesTree = new HashMap<String, Set<String>>();
    
    /**
     * Key is the missing Module URI
     * Value is the Set of XQuery Module URIs that require the missing Module indicated by the Key
     */
    final static Map<String, Set<String>> missingDependencies = new HashMap<String, Set<String>>();
    
    /**
     * The list of XQuerys that could not be compiled
     * for reasons other than missing dependencies
     */
    final static Set<String> invalidQueries = new HashSet<String>();
    
    @Override
    public void prepare(final int event, final DBBroker broker, final Txn transaction, final XmldbURI documentPath, final DocumentImpl existingDocument) throws TriggerException {
        
    }

    @Override
    public void finish(final int event, final DBBroker broker, final Txn transaction, final XmldbURI documentPath, final DocumentImpl document) {
        
    }

    @Override
    public void beforeCreateDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws TriggerException {
    }

    @Override
    public void afterCreateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        after(broker, document);
    }

    @Override
    public void beforeUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        before(broker, document);
    }

    @Override
    public void afterUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        after(broker, document);
    }

    @Override
    public void beforeCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        before(broker, document);
    }

    @Override
    public void afterCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        after(broker, document);
    }

    @Override
    public void beforeMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        before(broker, document);
    }

    @Override
    public void afterMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        after(broker, document);
    }

    @Override
    public void beforeDeleteDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        before(broker, document);
    }

    @Override
    public void afterDeleteDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws TriggerException {
        
    }
    
    @Override
    public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
    }

    @Override
    public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn txn, final DocumentImpl document) throws TriggerException {
    }
    
    private void before(final DBBroker broker, final DocumentImpl document) {
        if(isXquery(document)) {
            deregisterServices(broker, document.getURI());
        }
    }
    
    private void after(final DBBroker broker, final DocumentImpl document) throws TriggerException {
        if(isXquery(document)) {
            try {
                final List<RestXqService> services = findServices(broker, document);
                registerServices(broker, services);
            } catch(final ExQueryException eqe) {
               throw new TriggerException(eqe.getMessage(), eqe);
            }
        }
    }
    
    private boolean isXquery(final DocumentImpl document) {
         return document instanceof BinaryDocument && document.getMetadata().getMimeType().equals(XQueryCompiler.XQUERY_MIME_TYPE);
    }
    
    private void registerServices(final DBBroker broker, final List<RestXqService> services) {
        getRegistry(broker).register(services);
    }
    
    private void deregisterServices(final DBBroker broker, final XmldbURI xqueryLocation) {
        
        getRegistry(broker).deregister(xqueryLocation.getURI());
        
        //find and remove services from modules that depend on this one
        for(final String dependant : getDependants(xqueryLocation)) {
            try {
                getRegistry(broker).deregister(new URI(dependant));
            } catch(final URISyntaxException urise) {
                LOG.error(urise.getMessage(), urise);
            }
        }
        
        /*
         * update the missingDependencies??
         * Probaly not needed as this will be done in find services
         */
    }
    
    private Set<String> getDependants(final XmldbURI xqueryLocation) {
        final Set<String> dependants = new HashSet<String>();
        
        //make a copy of the dependenciesTree into depTree
        final Map<String, Set<String>> depTree;
        synchronized(dependenciesTree) {
            depTree = new HashMap<String, Set<String>>(dependenciesTree);
        }
        
        //find all modules that have a dependency on this one
        for(final Entry<String, Set<String>> depTreeEntry : depTree.entrySet()) {
            for(String dependency : depTreeEntry.getValue()) {
                if(dependency.equals(xqueryLocation.toString())) {
                    dependants.add(depTreeEntry.getKey());
                    continue;
                }
            }
        }
        return dependants;
    }
    
    private List<RestXqService> findServices(final DBBroker broker, final DocumentImpl document) throws ExQueryException {
        
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
        } catch(final RestXqServiceCompilationException rxsce) {

            //if there was a missing dependency then record it
            final String missingModuleHint = extractMissingModuleHint(rxsce);
            if(missingModuleHint != null) {
                recordMissingDependency(missingModuleHint, document.getURI());
            } else {
                recordInvalidQuery(document.getURI());
                LOG.error("XQuery '" + document.getURI() + "' could not be compiled! " + rxsce.getMessage(), rxsce);
            }

            /*
             * This may be the recompilation of a query
             * so we should unregister any of its missing
             * services. Luckily this is taken care of in
             * the before{EVENT} trigger functions
             */
        }
        
        return new ArrayList<RestXqService>();
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
                dependants = new HashSet(deps);
            } else {
                dependants = new HashSet();
            }
        }
        
        for(final String dependant : dependants) {
            
            try {
                LOG.info("Missing dependency '" + compiledModuleUri +"' has been added to the database, re-examining '" + dependant + "'...");
                
                final DocumentImpl dependantModule = broker.getResource(XmldbURI.create(dependant), Permission.READ);
                final List<RestXqService> services = findServices(broker, dependantModule);
                registerServices(broker, services);
            } catch(final PermissionDeniedException pde) {
                LOG.error(pde.getMessage(), pde);
            } catch(final ExQueryException eqe) {
                
            }
            
            //remove the resolve dependecies from the missing dependencies
            synchronized(missingDependencies) {
                final Set<String> missingDependants = missingDependencies.get(compiledModuleUri);
                missingDependants.remove(dependant);
                if(missingDependants.isEmpty()) {
                    missingDependencies.remove(compiledModuleUri);
                }
            }
        }
    }
    
    private void recordQueryDependenciesTree(final Map<String, Set<String>> queryDependenciesTree) {
        synchronized(dependenciesTree) {
            
            //Its not a merge its an ovewrite!
            dependenciesTree.putAll(queryDependenciesTree);
            
            /*
            for(final String key : queryDependenciesTree.keySet()) {
                Set<String> dependencies = dependenciesTree.get(key);
                if(dependencies == null) {
                    dependencies = queryDependenciesTree.get(key);
                } else {
                    dependencies.addAll(queryDependenciesTree.get(key));
                }
                dependenciesTree.put(key, dependencies); 
            }*/
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

    private String extractMissingModuleHint(final RestXqServiceCompilationException rxsce) {
        if(rxsce.getCause() instanceof XPathException) {
            final XPathException xpe = (XPathException)rxsce.getCause();
            if(xpe.getErrorCode() == ErrorCodes.XQST0059) {
                final Sequence errorVals = xpe.getErrorVal();
                if(errorVals != null && errorVals.getItemCount() == 1) {
                    final Item errorVal = errorVals.itemAt(0);
                    if(errorVal instanceof StringValue) {
                        return ((StringValue)errorVal).getStringValue();
                    }
                }
            }
        }
        return null;
    }
    
    private void recordMissingDependency(final String moduleHint, final XmldbURI xqueryUri) {
        final String moduleUri = getAbsoluteModuleHint(moduleHint, xqueryUri);
        
        synchronized(missingDependencies) {
            final Set<String> dependants;
            if(missingDependencies.containsKey(moduleUri)) {
                dependants = missingDependencies.get(moduleUri);
            } else {
                dependants = new HashSet<String>();
            }
            
            dependants.add(xqueryUri.toString());
            
            missingDependencies.put(moduleUri, dependants);
        }
        
        LOG.warn("Module '" + xqueryUri + "' has a missing dependency on '" + moduleUri + "'. Will re-examine if the missing module is added.");
    }
    
    private String getAbsoluteModuleHint(final String moduleHint, final XmldbURI xqueryUri) {
        if(moduleHint.startsWith(XmldbURI.ROOT_COLLECTION)) {
            return moduleHint;
        } else if(moduleHint.startsWith(XmldbURI.EMBEDDED_SERVER_URI.toString())) {
            return moduleHint.replace(XmldbURI.EMBEDDED_SERVER_URI.toString(), "");
        } else {
            return xqueryUri.removeLastSegment().append(moduleHint).toString();
        }
    }
    
    private RestXqServiceRegistry getRegistry(final DBBroker broker) {
        return RestXqServiceRegistryManager.getRegistry(broker.getBrokerPool());
    }
}