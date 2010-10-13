/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
import org.exist.security.utils.ConverterFrom1_0;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ConfigurationDocumentTrigger  extends FilteringTrigger {

	@Override
	public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
		;
	}

	@Override
	public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {

		Configuration conf = Configurator.hotConfigs.get(documentPath);
		if (conf != null) 
			conf.checkForUpdates((ElementAtExist) document.getDocumentElement());
		
		if (documentPath.equals("/db/system/users.xml")) {
			try {
				ConverterFrom1_0.convert(broker.getUser(),
						broker.getBrokerPool().getSecurityManager(),
						document);
			} catch (PermissionDeniedException e) {
			} catch (EXistException e) {
			}
		}

	}

}
