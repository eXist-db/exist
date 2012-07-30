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

import java.lang.reflect.Constructor;
import java.util.List;

import org.exist.Database;
import org.exist.backup.BackupHandler;
import org.exist.backup.RestoreHandler;
import org.exist.collections.Collection;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.DocumentImpl;
import org.exist.plugin.Jack;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;
import org.exist.util.serializer.SAXSerializer;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Plugin implements Jack, BackupHandler, RestoreHandler {
	
	public final static String PREFIX = "md";
	public final static String NAMESPACE_URI = "http://exist-db.org/metadata";

	public final static String UUID = "uuid";
	public final static String META = "meta";
	public final static String KEY = "key";
	public final static String VALUE = "value";
	public final static String VALUE_IS_DOCUMENT = "value-is-document";
	
	public final static String PREFIX_UUID = PREFIX+":"+UUID;
	public final static String PREFIX_KEY = PREFIX+":"+KEY;
	public final static String PREFIX_META = PREFIX+":"+META;
	public final static String PREFIX_VALUE = PREFIX+":"+VALUE;

	protected static Plugin _ = null;
	
	MetaData md;
	
	public Plugin(PluginsManager manager) throws PermissionDeniedException {
		try {
			@SuppressWarnings("unchecked")
			Class<? extends MetaData> backend = 
				(Class<? extends MetaData>) Class.forName("org.exist.storage.md.MetaDataImpl");
		
			Constructor<? extends MetaData> ctor = backend.getConstructor(Database.class);
			md = ctor.newInstance(manager.getDatabase());
		} catch (Exception e) {
			throw new PermissionDeniedException(e);
		}

		_ = this;

		manager.getDatabase().getDocumentTriggers().add(new DocumentEvents());
		manager.getDatabase().getCollectionTriggers().add(new CollectionEvents());
	}
	
	@Override
	public void sync() {
		md.sync();
	}

	@Override
	public void stop() {
		md.close();
	}

	//backup methods
	@Override
	public void backup(Collection colection, AttributesImpl attrs) {
	}

	@Override
	public void backup(Collection colection, SAXSerializer serializer) throws SAXException {
	}

	@Override
	public void backup(DocumentAtExist document, AttributesImpl attrs) {
		Metas ms = md.getMetas(document);
		
        attrs.addAttribute( NAMESPACE_URI, UUID, PREFIX_UUID, "CDATA", ms.getUUID() );
	}

	@Override
	public void backup(DocumentAtExist document, SAXSerializer serializer) throws SAXException {
		Metas ms = md.getMetas(document);
		
		List<Meta> sub = ms.metas();
		for (Meta m : sub) {

			AttributesImpl attr = new AttributesImpl();
	        attr.addAttribute(NAMESPACE_URI, UUID, PREFIX_UUID, "CDATA", m.getUUID());
	        attr.addAttribute(NAMESPACE_URI, KEY, PREFIX_KEY, "CDATA", m.getKey());
	        
	        Object value = m.getValue();
	        if (value instanceof DocumentImpl) {
				DocumentImpl doc = (DocumentImpl) value;
				
		        attr.addAttribute(NAMESPACE_URI, VALUE, PREFIX_VALUE, "CDATA", doc.getURI().toString());
		        attr.addAttribute(NAMESPACE_URI, VALUE_IS_DOCUMENT, PREFIX_VALUE, "CDATA", "true");
			
	        } else {
				
	        	attr.addAttribute(NAMESPACE_URI, VALUE, PREFIX_VALUE, "CDATA", value.toString());
			}

	        serializer.startElement(NAMESPACE_URI, META, PREFIX_META, attr );
	        serializer.endElement(NAMESPACE_URI, META, PREFIX_META);
		}
	}

	//restore methods
	
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
			
			md._addMeta(currentMetas, uuid, key, value);
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}

	@Override
	public void processingInstruction(String target, String data) throws SAXException {}

	@Override
	public void skippedEntity(String name) throws SAXException {}

	@Override
	public void startCollectionRestore(Collection colection, Attributes atts) {}

	@Override
	public void endCollectionRestore(Collection colection) {}

	@Override
	public void startDocumentRestore(DocumentAtExist document, Attributes atts) {
		System.out.println("startDocument "+document.getURI());
		String uuid = atts.getValue(NAMESPACE_URI, UUID);
		if (uuid != null)
			currentMetas = md.replaceMetas(document.getURI(), uuid);
		else
			currentMetas = md.addMetas(document); 
	}

	@Override
	public void endDocumentRestore(DocumentAtExist document) {
	}
}