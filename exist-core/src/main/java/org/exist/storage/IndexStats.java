/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import org.exist.storage.dom.DOMFile;
import org.exist.storage.index.BFile;



public class IndexStats {

	private BufferStats indexBufferStats = null;
	private BufferStats dataBufferStats = null;
	
	public IndexStats(BFile db) {
		indexBufferStats = db.getIndexBufferStats();
		dataBufferStats = db.getDataBufferStats();
	}
	
	public IndexStats(DOMFile db) {
		indexBufferStats = db.getIndexBufferStats();
		dataBufferStats = db.getDataBufferStats();
	}
	
	public BufferStats getIndexBufferStats() {
		return indexBufferStats;
	}
	
	public BufferStats getDataBufferStats() {
		return dataBufferStats;
	}
}
