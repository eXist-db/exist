
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.storage;

import java.util.Observable;
import java.util.TreeMap;

import org.apache.log4j.Category;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.util.Configuration;

/**
* ElementIndex collects all element occurrences. It uses the name of the
* element and the current doc_id as keys and stores all occurrences
* of this element in a blob. This means that the blob just contains
* an array of gid's which may be compressed if useCompression is true.
* Storing all occurrences in one large blob is much faster than storing
* each of them in a single table row.
*/
public abstract class ElementIndex extends Observable {

    protected DBBroker broker;
    protected TreeMap elementIds = new TreeMap();
    protected DocumentImpl doc;
    protected Configuration config;
    private static Category LOG = Category.getInstance(ElementIndex.class.getName());

    public ElementIndex(DBBroker broker, Configuration config) {
        this.broker = broker;
		this.config = config;
    }

    public void setDocument(DocumentImpl doc) {
        this.doc = doc;
    }
        
    public abstract void addRow(QName qname, NodeProxy proxy);

    public abstract void flush();
    
	public abstract void reindex(DocumentImpl oldDoc, NodeImpl node);
	
	public abstract void remove();
	
	public abstract void sync();
}
