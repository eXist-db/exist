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
package org.exist.collections;

import java.util.Iterator;
import java.util.Map;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.storage.DBBroker;
import org.w3c.dom.Document;

/**
 * @author wolf
 */
public class Dumper extends FilteringTrigger {

	/* (non-Javadoc)
	 * @see org.exist.collections.FilteringTrigger#configure(java.util.Map)
	 */
	public void configure(Map parameters)
		throws CollectionConfigurationException {
		System.out.println("parameters:");
		for(Iterator i = parameters.entrySet().iterator(); i.hasNext(); ) {
			Map.Entry next = (Map.Entry)i.next();
			System.out.println(next.getKey() + " = " + next.getValue());
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.collections.Trigger#prepare(org.exist.storage.DBBroker, org.exist.collections.Collection, java.lang.String, org.w3c.dom.Document)
	 */
	public void prepare(
		DBBroker broker,
		Collection collection,
		String documentName,
		Document existingDocument)
		throws TriggerException {
		System.out.println("\nstoring document " + documentName + " into collection " + collection.getName());
		if(existingDocument != null)
			System.out.println("replacing document " + ((DocumentImpl)existingDocument).getFileName());
		System.out.println("collection contents:");
		DocumentSet docs = new DocumentSet();
		collection.getDocuments(docs);
		for(int i = 0; i < docs.getLength(); i++)
			System.out.println("\t" + ((DocumentImpl)docs.item(i)).getFileName());
	}
}
