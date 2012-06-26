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

import org.exist.backup.BackupHandler;
import org.exist.collections.Collection;
import org.exist.dom.DocumentAtExist;
import org.exist.plugin.Jack;
import org.exist.plugin.PluginsManager;
import org.exist.security.PermissionDeniedException;
import org.exist.util.serializer.SAXSerializer;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.sleepycat.persist.EntityCursor;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Plugin implements Jack, BackupHandler {
	
	public final static String PREFIX = "md";
	public final static String NAMESPACE_URI = "http://exist-db.org/metadata";

	MetaDataImpl md;
	
	public Plugin(PluginsManager manager) throws PermissionDeniedException {
		md = new MetaDataImpl(manager.getDatabase());
		
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

	@Override
	public void backup(Collection colection, AttributesImpl attrs) {
	}

	@Override
	public void backup(Collection colection, SAXSerializer serializer) throws SAXException {
	}

	@Override
	public void backup(DocumentAtExist document, AttributesImpl attrs) {
		Metas ms = md.getMetas(document);
		
        attrs.addAttribute( NAMESPACE_URI, "uuid", PREFIX+":uuid", "CDATA", ms.getUUID() );
	}

	@Override
	public void backup(DocumentAtExist document, SAXSerializer serializer) throws SAXException {
		Metas ms = md.getMetas(document);
		
		EntityCursor<MetaImpl> sub = ms.keys();
		try {
			
			for (MetaImpl m : sub) {

				AttributesImpl attr = new AttributesImpl();
		        attr.addAttribute(NAMESPACE_URI, "key", PREFIX+":key", "CDATA", m.getKey());
		        attr.addAttribute(NAMESPACE_URI, "value", PREFIX+":value", "CDATA", m.getValue());

		        serializer.startElement(NAMESPACE_URI, "meta", PREFIX+":meta", attr );
		        serializer.endElement(NAMESPACE_URI, "meta", PREFIX+":meta");
			}

		} finally {
			sub.close();
		}
	}
}
