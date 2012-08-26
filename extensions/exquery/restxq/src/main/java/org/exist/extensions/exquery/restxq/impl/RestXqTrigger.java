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

import java.util.*;
import org.apache.log4j.Logger;

import org.exist.collections.Collection;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
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
    
    final static Map<String, Set<String>> dependenciesTree = new HashMap<String, Set<String>>();
    final static Map<String, Set<String>> missingDependencies = new HashMap<String, Set<String>>();
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
        //TOOD ideally the compilation step would be in beforeCreateDocument - but we cant access the new module source at that point!
        final List<RestXqService> services = findServices(broker, document);
        registerServices(broker, services);
    }

    @Override
    public void beforeUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        deregisterServices(broker, document.getURI());
    }

    @Override
    public void afterUpdateDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        //TOOD ideally the compilation step would be in beforeUpdateDocument - but we cant access the new module source at that point!
        final List<RestXqService> services = findServices(broker, document);
        registerServices(broker, services);
    }

    @Override
    public void beforeCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        
    }

    @Override
    public void afterCopyDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
     
    }

    @Override
    public void beforeMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
     
    }

    @Override
    public void afterMoveDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document, final XmldbURI oldUri) throws TriggerException {
        
    }

    @Override
    public void beforeDeleteDocument(final DBBroker broker, final Txn transaction, final DocumentImpl document) throws TriggerException {
        deregisterServices(broker, document.getURI());
    }

    @Override
    public void afterDeleteDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) throws TriggerException {
        
    }
    
    private void registerServices(final DBBroker broker, final List<RestXqService> services) throws TriggerException {
        getRegistry(broker).register(services);
    }
    
    private void deregisterServices(final DBBroker broker, final XmldbURI xqueryLocation) throws TriggerException{
        getRegistry(broker).deregister(xqueryLocation.getURI());
        
        //TODO check the dependencies tree:
        //1) remove dependant xquery module services
        //2) update the missingDependencies 
    }
    
    private List<RestXqService> findServices(final DBBroker broker, final DocumentImpl document) throws TriggerException {
        
        //ensure we have a binary document with the correct mimetype
        if(document instanceof BinaryDocument) {
            final DocumentMetadata metadata = document.getMetadata();
            if(metadata.getMimeType().equals(XQueryCompiler.XQUERY_MIME_TYPE)){
                try {
                    final CompiledXQuery compiled = XQueryCompiler.compile(broker, document);
                    
                    /*
                     * examine the compiled query, record all modules and modules of modules.
                     * Keep a dependencies list so that we can act on it if a module is deleted.
                     */ 
                    final Map<String, Set<String>> queryDependenciesTree = XQueryInspector.getDependencies(compiled);
                    recordQueryDependenciesTree(queryDependenciesTree);
                    
                    //TODO
                    
                    //2) re-compile and examine any queries with missing dependencies,
                    //that have been resolved (just call this function and register fn per module) (could/should be done asynchronously)
                    
                    //3) remove any re-compiled query from the invalid queries list
                    
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
                } catch(final ExQueryException eqe) {
                    throw new TriggerException(eqe.getMessage(), eqe);
                }
            }
        }
        
        return new ArrayList<RestXqService>();
    }
    
    private void recordQueryDependenciesTree(final Map<String, Set<String>> queryDependenciesTree) {
        synchronized(dependenciesTree) {
            for(final String key : queryDependenciesTree.keySet()) {
                Set<String> dependencies = dependenciesTree.get(key);
                if(dependencies == null) {
                    dependencies = queryDependenciesTree.get(key);
                } else {
                    dependencies.addAll(queryDependenciesTree.get(key));
                }
                
                dependenciesTree.put(key, dependencies);
            }
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

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
	}
}