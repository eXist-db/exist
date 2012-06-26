/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.storage.md;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XmldbURI;

import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.persist.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class MetaDataImpl extends MetaData {
	
	protected static MetaDataImpl _ = null;

	private Environment env;
    private EntityStore store;
    
    private PrimaryIndex<String, MetasImpl> docByUUID;

    private SecondaryIndex<String, String, MetasImpl> uriToDoc;

    private PrimaryIndex<String, MetaImpl> metadataByUUID;
    private SecondaryIndex<String, String, MetaImpl> metadata;

    private SecondaryIndex<String, String, MetaImpl> keyToMeta;
    private SecondaryIndex<String, String, MetaImpl> valueToMeta;

    public MetaDataImpl(Database db) {
    	
    	LOG.debug("initializing metadata storage");
    	
    	File folder = db.getStoragePlace();
    	
    	File dataDirectory = new File(folder, "metadata"); 
		dataDirectory.mkdirs();
		
		LOG.debug("folder created ... ");
		
		EnvironmentConfig envConfig = new EnvironmentConfig();
		envConfig.setAllowCreate(true);
		envConfig.setTransactional(false);
		env = new Environment(dataDirectory, envConfig);

		LOG.debug("environment ... ");

		StoreConfig storeConfig = new StoreConfig();
		storeConfig.setAllowCreate(true);
		storeConfig.setTransactional(false);
		store = new EntityStore(env, "md", storeConfig);
		
		LOG.debug("entity store ... ");

		docByUUID = store.getPrimaryIndex(String.class, MetasImpl.class);
		
		uriToDoc = store.getSecondaryIndex(docByUUID, String.class, "uri");
		
		metadataByUUID = store.getPrimaryIndex(String.class, MetaImpl.class);
		metadata = store.getSecondaryIndex(metadataByUUID, String.class, "object");

		keyToMeta = store.getSecondaryIndex(metadataByUUID, String.class, "key");
		valueToMeta = store.getSecondaryIndex(metadataByUUID, String.class, "value");

		LOG.debug("ready ... ");

		MetaDataImpl._ = this;
		MetaData._ = this;

		LOG.debug("done.");
    }
	
	public DocumentImpl getDocument(String uuid) throws EXistException, PermissionDeniedException {
		MetasImpl ms = docByUUID.get(uuid);
		if (ms == null) return null;
		
		BrokerPool db = null;
		DBBroker broker = null;
		try {
			db = BrokerPool.getInstance();
			broker = db.get(null);
			
			XmldbURI uri = XmldbURI.create(ms.uri);
			Collection col = broker.getCollection(uri.removeLastSegment());
			if (col != null) {
				return col.getDocument(broker, uri.lastSegment());
			}
			
			return null;
		} finally {
			if (db != null)
				db.release(broker);
		}
	}

    private Metas _addMetas(DocumentImpl doc) {
		MetasImpl d = new MetasImpl(doc);
		docByUUID.put(d);
		
		if (LOG.isDebugEnabled())
			LOG.debug("addMetas "+d.getUUID()+" "+doc.getURI());

		return d;
	}

	public Metas addMetas(DocumentImpl doc) {
    	Metas _d = getMetas(doc.getURI(), false);
    	
    	if (_d != null)
    		return _d;
    	
		return _addMetas(doc);
	}

    public Metas getMetas(DocumentAtExist doc) {
    	return getMetas(doc.getURI(), true);
    }

    public Metas getMetas(XmldbURI uri) {
    	return getMetas(uri, true);
    }

    public Metas getMetas(XmldbURI uri, boolean addIfmissing) {
    	
    	if (LOG.isDebugEnabled())
			LOG.debug("getMetas "+uri+" ");
    	System.out.println("getMetas "+uri+" ");
		
		EntityJoin<String, MetasImpl> join = new EntityJoin<String, MetasImpl>(docByUUID);
		join.addCondition(uriToDoc, uri.toString());
		
		ForwardCursor<MetasImpl> entities = join.entities();
		try { 
			MetasImpl v = entities.next();
			if (LOG.isDebugEnabled()) {
				MetasImpl n;
				while ((n = entities.next()) != null) {
					LOG.error("ERROR "+n.getUUID()+" "+uri);
				}
			}
//			System.out.println(v);
//			
//			
//			//this possible a bug, but NPE should be avoided (TODO: write lock required) 
//			if (addIfmissing && v == null) {
//				
//				//check that document exist
//				BrokerPool pool = null;
//				DBBroker broker = null;
//				try {
//					pool = BrokerPool.getInstance();
//					broker = pool.get(null);
//					
//					Collection col = broker.getCollection(uri.removeLastSegment());
//					if (col != null) {
//						DocumentImpl _doc = col.getDocument(broker, uri.lastSegment());
//						
//						if (_doc != null) {
//							LOG.error("metas for document "+uri+" get lost!");
//							return _addMetas(_doc);
//						}
//
//					}
//				} catch (Exception e) {
//					LOG.error(e);
//					return null;
//					
//				} finally {
//					if (pool != null)
//						pool.release(broker);
//				}
//				
//				return null;
//			}
			return v;
		} finally {
			entities.close();
		}
	}

	public void delMetas(XmldbURI uri) {
		Metas d = getMetas(uri, false);
		
		if (d != null) {
			if (LOG.isDebugEnabled())
				LOG.debug("delete metas "+d.getUUID()+" "+uri);
			
			delMetas(d);

		} else {
			if (LOG.isDebugEnabled())
				LOG.debug("delete metas NULL "+uri);
		}
	}

	private void delMetas(Metas d) {
		EntityCursor<MetaImpl> sub = metadata.subIndex(d.getUUID()).entities();
		try {
			for (MetaImpl m : sub)
				metadataByUUID.delete(m.getUUID());

		} finally {
			sub.close();
		}

		docByUUID.delete(d.getUUID());
	}

	protected MetaImpl addMeta(Metas doc, String key, String value) {
		MetaImpl m = new MetaImpl(doc.getUUID(), key, value);
		metadataByUUID.put(m);
		
		return m;
	}

	protected MetaImpl addMeta(Metas doc, String uuid, String key, String value) {
		MetaImpl m = new MetaImpl(doc.getUUID(), uuid, key, value);
		metadataByUUID.put(m);
		
		return m;
	}

	protected MetaImpl setMeta(MetaImpl meta) {
		metadataByUUID.put(meta);
		
		return meta;
	}

	protected Meta addMeta(Meta meta) {
		if (meta instanceof MetaImpl) {
			return setMeta((MetaImpl) meta);
		}
		throw new RuntimeException("unsupported operation ["+meta+"]");
	}

	protected Meta getMeta(Metas doc, String key) {
		//System.out.println("key = "+key);

		EntityCursor<MetaImpl> sub = metadata.subIndex(doc.getUUID()).entities();
		try {
			for (MetaImpl m : sub)
				if (m.getKey().equals(key))
					return m;

		} finally {
			sub.close();
		}
		return null;
	}

	public List<DocumentImpl> matchDocuments(String key, String value) throws EXistException {
		
		EntityJoin<String, MetaImpl> join = new EntityJoin<String, MetaImpl>(metadataByUUID);
		join.addCondition(keyToMeta, key);
		join.addCondition(valueToMeta, value);
		
		ForwardCursor<MetaImpl> entities = join.entities();
		try { 
			List<DocumentImpl> list = new ArrayList<DocumentImpl>();
			for (MetaImpl entity : entities) {
				try {
					list.add(getDocument(entity.getObject()));
				} catch (PermissionDeniedException ex) {
					//ignore
				}
			}
			return list;
		} finally {
			entities.close();
		}
	}
	
	public void close() {
		store.close();
		env.close();
	}

	public void sync() {
		store.sync();
		env.sync();
	}

//	public void moveMetas(Metas metas, DocumentImpl doc) {
//		if (metas instanceof MetasImpl) {
//			MetasImpl ms = (MetasImpl) metas;
//			ms.update(doc);
//			
//			//docByUUID.put(ms);
//			
//			return;
//		}
//		throw new RuntimeException("unsupported operation ["+metas+"]");
//	}

	public void moveMetas(XmldbURI oldUri, XmldbURI newUri) {
		MetasImpl ms = (MetasImpl)getMetas(oldUri);
		
		System.out.println("moveMetas "+oldUri+" to "+newUri);
		if(ms != null){
			ms.uri = newUri.toString();
			
			docByUUID.put(ms);

			ms = (MetasImpl)getMetas(oldUri);
			System.out.println(ms);
		}else
		{
			throw new RuntimeException("Metas NULL for document: " + oldUri + " in moveMetas");
			//LOG.warn("Metas NULL for document: " + doc.getURI() + " in moveMetas");
		}
//		Map<String, String> map = new HashMap<String, String>();
//		
//		if(ms != null)
//		{
//			EntityCursor<MetaImpl> sub = metadata.subIndex(ms.getUUID()).entities();
//			try {
//				for (MetaImpl m : sub)
//					map.put(m.getKey(), m.getValue());
//	
//			} finally {
//				sub.close();
//			}
//		}
//		
//		delMetas(ms);
//		
//		MetasImpl newMs = new MetasImpl((MetasImpl)ms, uri);

		return;
	}

//	public void updateMetas(XmldbURI oldD, DocumentImpl newD) {
//		MetasImpl ms = (MetasImpl)getMetas(oldD);
//		ms.update(newD);
//	}

	public void copyMetas(XmldbURI oldDoc, DocumentImpl newDoc) {
		MetasImpl ms = (MetasImpl)getMetas(oldDoc);
		
		MetasImpl newMs = (MetasImpl) addMetas(newDoc);

		if(ms != null)
		{
			EntityCursor<MetaImpl> sub = metadata.subIndex(ms.getUUID()).entities();
			try {
				for (MetaImpl m : sub)
					newMs.put(m.getKey(), m.getValue());
	
			} finally {
				sub.close();
			}
		}
	}

	public EntityCursor<MetaImpl> getMetaKeys(Metas doc) {
		return metadata.subIndex(doc.getUUID()).entities();
	}
}