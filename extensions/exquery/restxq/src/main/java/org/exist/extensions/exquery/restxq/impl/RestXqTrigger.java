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
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceRegistry;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class RestXqTrigger extends FilteringTrigger {
    
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
    }
    
    private List<RestXqService> findServices(final DBBroker broker, final DocumentImpl document) throws TriggerException {
        
        //ensure we have a binary document with the correct mimetype
        if(document instanceof BinaryDocument) {
            final DocumentMetadata metadata = document.getMetadata();
            if(metadata.getMimeType().equals(XQueryCompiler.XQUERY_MIME_TYPE)){
                try {
                    final CompiledXQuery compiled = XQueryCompiler.compile(broker, document);
                    return XQueryInspector.findServices(compiled);
                } catch(final RestXqServiceCompilationException rxsce) {
                    throw new TriggerException(rxsce.getMessage(), rxsce);
                } catch(final ExQueryException eqe) {
                    throw new TriggerException(eqe.getMessage(), eqe);
                }
            }
        }
        
        return new ArrayList<RestXqService>();
    }
    
    private RestXqServiceRegistry getRegistry(final DBBroker broker) {
        return RestXqServiceRegistryManager.getRegistry(broker.getBrokerPool());
    }
}