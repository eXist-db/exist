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

import java.util.List;
import org.exist.collections.triggers.SAXTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exquery.ExQueryException;
import org.exquery.restxq.RestXqService;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RestXqTrigger extends SAXTrigger {
    
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
        final ExistXqueryRegistry xqueryRegistry = ExistXqueryRegistry.getInstance();
        if(xqueryRegistry.isXquery(document)) {
            xqueryRegistry.deregisterServices(broker, document.getURI());
        }
    }
    
    private void after(final DBBroker broker, final DocumentImpl document) throws TriggerException {
        final ExistXqueryRegistry xqueryRegistry = ExistXqueryRegistry.getInstance();
        if(xqueryRegistry.isXquery(document)) {
            try {
                final List<RestXqService> services = xqueryRegistry.findServices(broker, document);
                xqueryRegistry.registerServices(broker, services);
            } catch(final ExQueryException eqe) {
               throw new TriggerException(eqe.getMessage(), eqe);
            }
        }
    }
}