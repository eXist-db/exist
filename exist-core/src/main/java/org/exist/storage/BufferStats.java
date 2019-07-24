/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2019 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

/*
 * BufferStats.java - Apr 5, 2003
 *
 * @author wolf
 */
public class BufferStats {
	
	public final static int INDEX = 0;
	public final static int DATA = 1;

	//private int type = 0;
	private int size = 0;
	private int used = 0;
	private int pageFails = 0;
	private int pageHits = 0;

	public BufferStats(int size, int used, int hits, int fails) {
		this.size = size;
		this.used = used;
		this.pageHits = hits;
		this.pageFails = fails;
	}

	public int getPageFails() {
		return pageFails;
	}

	public int getPageHits() {
		return pageHits;
	}

	public int getSize() {
		return size;
	}

	public int getUsed() {
		return used;
	}

}
