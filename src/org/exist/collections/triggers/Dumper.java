/*
 *  Dumper.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 * $Id$
 *
 */
package org.exist.collections.triggers;

import java.util.Map.Entry;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

import java.util.List;
import java.util.Map;

/**
 * @author wolf
 */
public class Dumper extends FilteringTrigger {

	/* (non-Javadoc)
	 * @see org.exist.collections.FilteringTrigger#configure(java.util.Map)
	 */
	public void configure(DBBroker broker, Collection parent, Map<String, List> parameters)
		throws CollectionConfigurationException {
		super.configure(broker, parent, parameters);
		System.out.println("parameters:");
                
                for(Entry<String, List> entry : parameters.entrySet()) {
                    System.out.print(entry.getKey() + " = " + entry.getValue());
                }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#prepare(org.exist.storage.DBBroker, org.exist.collections.Collection, java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(
		int event,
		DBBroker broker,
		Txn transaction,
		XmldbURI documentName, 
		DocumentImpl existingDocument)
		throws TriggerException {
		System.out.println("\nstoring document " + documentName + " into collection " + collection.getURI());
		if(existingDocument != null)
			System.out.println("replacing document " + ((DocumentImpl)existingDocument).getFileURI());
		System.out.println("collection contents:");
		DefaultDocumentSet docs = new DefaultDocumentSet();
		collection.getDocuments(broker, docs, true);
		for(int i = 0; i < docs.getDocumentCount(); i++)
			System.out.println("\t" + docs.getDocumentAt(i).getFileURI());
	}

    /* (non-Javadoc)
     * @see org.exist.collections.triggers.DocumentTrigger#finish(int, org.exist.storage.DBBroker, java.lang.String, org.w3c.dom.Document)
     */
    public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
    }
}
