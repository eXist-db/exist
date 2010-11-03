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
package org.exist.synchro;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.jgroups.blocks.MethodCall;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class WatchCollection implements CollectionTrigger {

	private Communicator comm = null;
	
	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.Trigger#configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)
	 */
	@Override
	public void configure(DBBroker broker, Collection parent, Map<String, List<?>> parameters) throws CollectionConfigurationException {
		List<?> objs = parameters.get(Communicator.COMMUNICATOR);
		if (objs != null)
			comm = (Communicator) objs.get(0);
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.Trigger#getLogger()
	 */
	@Override
	public Logger getLogger() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.CollectionTrigger#prepare(int, org.exist.storage.DBBroker, org.exist.storage.txn.Txn, org.exist.collections.Collection, java.lang.String)
	 */
	@Override
	public void prepare(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) throws TriggerException {
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.CollectionTrigger#finish(int, org.exist.storage.DBBroker, org.exist.storage.txn.Txn, org.exist.collections.Collection, java.lang.String)
	 */
	@Override
	public void finish(int event, DBBroker broker, Txn transaction, Collection collection, Collection newCollection) {
	}

	@Override
	public void beforeCreateCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_CREATE_COLLECTION, comm.getChannel().getName(), uri) );
	}

	@Override
	public void afterCreateCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_CREATE_COLLECTION, comm.getChannel().getName(), collection.getURI()) );
	}

	@Override
	public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_COPY_COLLECTION, comm.getChannel().getName(), collection.getURI()) );
	}

	@Override
	public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_COPY_COLLECTION, comm.getChannel().getName(), collection.getURI()) );
	}

	@Override
	public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_MOVE_COLLECTION, comm.getChannel().getName(), collection.getURI()) );
	}

	@Override
	public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_MOVE_COLLECTION, comm.getChannel().getName(), collection.getURI()) );
	}

	@Override
	public void beforeDeleteCollection(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_DELETE_COLLECTION, comm.getChannel().getName(), collection.getURI()) );
	}

	@Override
	public void afterDeleteCollection(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_DELETE_COLLECTION, comm.getChannel().getName(), uri) );
	}

}
