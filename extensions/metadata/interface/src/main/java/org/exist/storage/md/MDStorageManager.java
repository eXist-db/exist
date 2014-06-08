/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.storage.md;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.backup.BackupHandler;
import org.exist.backup.RestoreHandler;
import org.exist.collections.Collection;
import org.exist.config.Configuration;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.plugin.Plug;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.MetaStorage;
import org.exist.storage.md.xquery.MetadataModule;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.XQueryContext;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class MDStorageManager implements Plug, BackupHandler, RestoreHandler {
	
    protected final static Logger LOG = Logger.getLogger(MDStorageManager.class);

    public final static String PREFIX = "md";
	public final static String NAMESPACE_URI = "http://exist-db.org/metadata";
	
    public final static String LUCENE_ID = "org.exist.indexing.lucene.LuceneIndex";

	public final static String UUID = "uuid";
	public final static String META = "meta";
	public final static String KEY = "key";
	public final static String VALUE = "value";
	public final static String VALUE_IS_DOCUMENT = "value-is-document";
	
	public final static String PREFIX_UUID = PREFIX+":"+UUID;
	public final static String PREFIX_KEY = PREFIX+":"+KEY;
	public final static String PREFIX_META = PREFIX+":"+META;
	public final static String PREFIX_VALUE = PREFIX+":"+VALUE;
	public final static String PREFIX_VALUE_IS_DOCUMENT = PREFIX+":"+VALUE_IS_DOCUMENT;

	protected static MDStorageManager inst = null;

    protected static MDStorageManager get() {
        return inst;
    }
	
	MetaData md;
	
	public MDStorageManager(PluginsManager manager) throws PermissionDeniedException {
		try {
			@SuppressWarnings("unchecked")
			Class<? extends MetaData> backend = 
				(Class<? extends MetaData>) Class.forName("org.exist.storage.md.MetaDataImpl");
		
			Constructor<? extends MetaData> ctor = backend.getConstructor(Database.class);
			md = ctor.newInstance(manager.getDatabase());
		} catch (Exception e) {
			e.printStackTrace();
			throw new PermissionDeniedException(e);
		}

        inst = this;
		
		Database db = manager.getDatabase();
		
		inject(db, md);

		db.registerDocumentTrigger(DocumentEvents.class);
		db.registerCollectionTrigger(CollectionEvents.class);
		
		//XXX: configuration is not loaded
//		try {
//			db.getIndexManager().registerIndex(new ExtractorIndex());
//		} catch (DatabaseConfigurationException e) {
//			e.printStackTrace();
//			throw new PermissionDeniedException(e);
//		}
		
		Map<String, Class<?>> map = (Map<String, Class<?>>) db.getConfiguration().getProperty(XQueryContext.PROPERTY_BUILT_IN_MODULES);
        map.put(
    		NAMESPACE_URI, 
    		MetadataModule.class);
	}
	
	private void inject(Database db, MetaStorage md) {
	    try {
            Field field = db.getClass().getDeclaredField("metaStorage");
            field.setAccessible(true);
            field.set(db, md);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
	    
	}
	

	@Override
	public void start(DBBroker broker) throws EXistException {
	}

	@Override
	public void sync(DBBroker broker) throws EXistException {
		md.sync();
	}

	@Override
	public void stop(DBBroker broker) throws EXistException {
		md.close();
	}

	//backup methods
	private void backup(Metas ms, AttributesImpl attrs) {
        attrs.addAttribute( NAMESPACE_URI, UUID, PREFIX_UUID, "CDATA", ms.getUUID() );
	}

	private void backup(Metas ms, SAXSerializer serializer) throws SAXException {
		List<Meta> sub = ms.metas();
		for (Meta m : sub) {

			AttributesImpl attr = new AttributesImpl();
	        attr.addAttribute(NAMESPACE_URI, UUID, PREFIX_UUID, "CDATA", m.getUUID());
	        attr.addAttribute(NAMESPACE_URI, KEY, PREFIX_KEY, "CDATA", m.getKey());
	        
	        Object value = m.getValue();
	        if (value instanceof DocumentImpl) {
				DocumentImpl doc = (DocumentImpl) value;
				
		        attr.addAttribute(NAMESPACE_URI, VALUE, PREFIX_VALUE, "CDATA", doc.getURI().toString());
		        attr.addAttribute(NAMESPACE_URI, VALUE_IS_DOCUMENT, PREFIX_VALUE_IS_DOCUMENT, "CDATA", "true");
			
	        } else {
				
	        	attr.addAttribute(NAMESPACE_URI, VALUE, PREFIX_VALUE, "CDATA", value.toString());
			}

	        serializer.startElement(NAMESPACE_URI, META, PREFIX_META, attr );
	        serializer.endElement(NAMESPACE_URI, META, PREFIX_META);
		}
	}

	@Override
	public void backup(Collection collection, AttributesImpl attrs) {
	    if (collection == null)
	        return;
	    
//		System.out.println("backup collection "+colection.getURI());
	    Metas ms = md.getMetas(collection.getURI());
	    if (ms != null)
	    	backup(ms, attrs);
	    else
	    	LOG.error("Collection '"+collection.getURI()+"' have no metas");
	}

	@Override
	public void backup(Collection collection, SAXSerializer serializer) throws SAXException {
	    if (collection == null)
	        return;
	    
//		System.out.println("backup collection "+colection.getURI());
	    Metas ms = md.getMetas(collection.getURI());
	    if (ms != null)
	    	backup(ms, serializer);
//	    else
//	    	LOG.error("Collection '"+collection.getURI()+"' have no metas");
	}

	@Override
	public void backup(DocumentAtExist document, AttributesImpl attrs) {
	    if (document == null)
	        return;
	    
//		System.out.println("backup document "+document.getURI());
	    Metas ms = md.getMetas(document);
	    if (ms != null)
	    	backup(ms, attrs);
	    else
	    	LOG.error("Document '"+document.getURI()+"' have no metas");
	    	
	}

	@Override
	public void backup(DocumentAtExist document, SAXSerializer serializer) throws SAXException {
	    if (document == null)
	        return;
	    
//		System.out.println("backup document "+document.getURI());
	    Metas ms = md.getMetas(document);
	    if (ms != null)
	    	backup(ms, serializer);
//	    else
//	    	LOG.error("Document '"+document.getURI()+"' have no metas");
	}

	//restore methods
	private Metas collectionMetas = null;
	private Metas currentMetas = null;

	@Override
	public void setDocumentLocator(Locator locator) {}

	@Override
	public void startDocument() throws SAXException {}

	@Override
	public void endDocument() throws SAXException {}

	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		if (META.equals(localName) && NAMESPACE_URI.equals(uri)) {
			String uuid = atts.getValue(NAMESPACE_URI, UUID);
			String key = atts.getValue(NAMESPACE_URI, KEY);
			String value = atts.getValue(NAMESPACE_URI, VALUE);
			
			if (currentMetas == null) {
				md._addMeta(collectionMetas, uuid, key, value);
			} else {
				md._addMeta(currentMetas, uuid, key, value);
			}
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
        if (localName.equals("collection")) {
        	;
        } else if (localName.equals("resource")) {
    		currentMetas = null;
        }
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}

	@Override
	public void processingInstruction(String target, String data) throws SAXException {}

	@Override
	public void skippedEntity(String name) throws SAXException {}

	@Override
	public void startCollectionRestore(Collection collection, Attributes atts) {
	    if (collection == null)
	        return;
	    
//		System.out.println("startCollectionRestore "+colection.getURI());
		String uuid = atts.getValue(NAMESPACE_URI, UUID);
		if (uuid != null)
			collectionMetas = md.replaceMetas(collection.getURI(), uuid);
		else
			collectionMetas = md.addMetas(collection); 
	}

	@Override
	public void endCollectionRestore(Collection collection) {
//		System.out.println("endCollectionRestore "+colection.getURI());
	}

	@Override
	public void startDocumentRestore(DocumentAtExist document, Attributes atts) {
	    if (document == null)
	        return;
	    
//		System.out.println("startDocument "+document.getURI());
		String uuid = atts.getValue(NAMESPACE_URI, UUID);
		if (uuid != null)
			currentMetas = md.replaceMetas(document.getURI(), uuid);
		else
			currentMetas = md.addMetas(document); 
	}

	@Override
	public void endDocumentRestore(DocumentAtExist document) {
//		System.out.println("endDocumentRestore "+document.getURI());
	}

	@Override
	public boolean isConfigured() {
		return true;
	}

	@Override
	public Configuration getConfiguration() {
		return null;
	}
}