/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.config;

import org.exist.EXistException;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementAtExist;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.utils.ConverterFrom1_0;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurationDocumentTrigger extends FilteringTrigger {

	@Override
	public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
		;
	}

	@Override
	public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {

		Configuration conf;
		
		switch (event) {
		case REMOVE_DOCUMENT_EVENT:
			conf = Configurator.getConfigurtion(broker.getBrokerPool(), documentPath);
			if (conf != null) {
				Configurator.unregister(conf);
				; //XXX: inform object that configuration was deleted
			}
			break;

		default:
			conf = Configurator.getConfigurtion(broker.getBrokerPool(), documentPath);
			if (conf != null) 
				conf.checkForUpdates((ElementAtExist) document.getDocumentElement());
			
			if (documentPath.equals("/db/system/users.xml")) {
				try {
					ConverterFrom1_0.convert(broker.getSubject(),
							broker.getBrokerPool().getSecurityManager(),
							document);
				} catch (PermissionDeniedException e) {
				} catch (EXistException e) {
				}
			}

			break;
		}
	}
	
	private void checkForUpdates(DBBroker broker, XmldbURI uri, DocumentImpl document) {
		Configuration conf = Configurator.getConfigurtion(broker.getBrokerPool(), uri);
		if (conf != null) 
			conf.checkForUpdates((ElementAtExist) document.getDocumentElement());
		
		if (uri.equals("/db/system/users.xml")) {
			try {
				ConverterFrom1_0.convert(broker.getSubject(),
						broker.getBrokerPool().getSecurityManager(),
						document);
			} catch (PermissionDeniedException e) {
			} catch (EXistException e) {
			}
		}
	}

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		//check saving list
		if (Configurator.saving.contains(Configurator.getFullURI(broker.getBrokerPool(), document.getURI()) ))
			return;
		
		checkForUpdates(broker, document.getURI(), document);
		
		XmldbURI uri = document.getCollection().getURI();
		if (uri.startsWith(SecurityManager.SECURITY_COLLETION_URI))
			try {
				broker.getBrokerPool().getSecurityManager().processPramatter(broker, document);
			} catch (ConfigurationException e) {
				LOG.error("Configuration can't be proccessed ["+document.getURI()+"]", e);
			}
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		checkForUpdates(broker, document.getURI(), document);
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
		checkForUpdates(broker, document.getURI(), document);
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
		checkForUpdates(broker, document.getURI(), document);
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		Configuration conf = Configurator.getConfigurtion(broker.getBrokerPool(), uri);
		if (conf != null) {
			Configurator.unregister(conf);
			; //XXX: inform object that configuration was deleted
		}
	}
}
