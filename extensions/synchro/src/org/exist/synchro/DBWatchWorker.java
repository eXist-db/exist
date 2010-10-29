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

import java.util.Map;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.indexing.IndexController;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.MatchListener;
import org.exist.indexing.StreamListener;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DBWatchWorker implements IndexWorker {

	private DBWatch dbWatch;
	
	private DocumentImpl document = null;
	private int mode = StreamListener.UNKNOWN;
	private Communicator cluster = null;
	
	public DBWatchWorker(DBWatch dbWatch) {
		this.dbWatch = dbWatch;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getIndexId()
	 */
	@Override
	public String getIndexId() {
		return dbWatch.getIndexId();
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getIndexName()
	 */
	@Override
	public String getIndexName() {
		return dbWatch.getIndexName();
	}
	
	protected Communicator getCluster() {
		if (cluster == null)
			cluster = dbWatch.getSynchro().getCluster(getDocument());
		
		return cluster;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#configure(org.exist.indexing.IndexController, org.w3c.dom.NodeList, java.util.Map)
	 */
	@Override
	public Object configure(IndexController controller, NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#setDocument(org.exist.dom.DocumentImpl)
	 */
	@Override
	public void setDocument(DocumentImpl doc) {
		document = doc;
		cluster = null;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#setDocument(org.exist.dom.DocumentImpl, int)
	 */
	@Override
	public void setDocument(DocumentImpl doc, int mode) {
		setDocument(doc);
		setMode(mode);
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#setMode(int)
	 */
	@Override
	public void setMode(int mode) {
		this.mode = mode;
		
		
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getDocument()
	 */
	@Override
	public DocumentImpl getDocument() {
		return document;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getMode()
	 */
	@Override
	public int getMode() {
		return mode;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getReindexRoot(org.exist.dom.StoredNode, org.exist.storage.NodePath, boolean)
	 */
	@Override
	public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getListener()
	 */
	@Override
	public StreamListener getListener() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#getMatchListener(org.exist.storage.DBBroker, org.exist.dom.NodeProxy)
	 */
	@Override
	public MatchListener getMatchListener(DBBroker broker, NodeProxy proxy) {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#flush()
	 */
	@Override
	public void flush() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#removeCollection(org.exist.collections.Collection, org.exist.storage.DBBroker)
	 */
	@Override
	public void removeCollection(Collection collection, DBBroker broker) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#checkIndex(org.exist.storage.DBBroker)
	 */
	@Override
	public boolean checkIndex(DBBroker broker) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.indexing.IndexWorker#scanIndex(org.exist.xquery.XQueryContext, org.exist.dom.DocumentSet, org.exist.dom.NodeSet, java.util.Map)
	 */
	@Override
	public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map hints) {
		return null;
	}

}
