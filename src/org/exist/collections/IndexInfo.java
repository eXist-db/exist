/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.collections;

import org.exist.Indexer;
import org.exist.dom.DocumentImpl;
import org.exist.util.serializer.DOMStreamer;
import org.xml.sax.XMLReader;

/**
 * Internal class used to track some required fields when calling
 * {@link org.exist.collections.Collection#validate(DBBroker, String, Node)} and
 * {@link org.exist.collections.Collection#store(DBBroker, IndexInfo, Node, boolean)}.
 * This class is not publicly readable.
 * 
 * @author wolf
 */
public class IndexInfo {

	protected Indexer indexer;
	protected XMLReader reader = null;
	protected DOMStreamer streamer = null;
	
	protected IndexInfo(Indexer indexer) {
		this.indexer = indexer;
	}
	
	protected Indexer getIndexer() {
		return indexer;
	}
	
	protected void setReader(XMLReader reader) {
		this.reader = reader;
	}
	
	protected XMLReader getReader() {
		return this.reader;
	}
	
	public void setDOMStreamer(DOMStreamer streamer) {
		this.streamer = streamer;
	}
	
	public DOMStreamer getDOMStreamer() {
		return this.streamer;
	}
	
	public DocumentImpl getDocument() {
		return indexer.getDocument();
	}
}
