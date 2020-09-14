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
package org.exist.storage.index;

import org.exist.storage.btree.Paged.Page;
import org.exist.xquery.Constants;



/**
 * Used to track the available amount of free space in a data page.
 * 
 * @see FreeList
 * @author wolf
 */
public class FreeSpace {

	protected int free = 0;
    protected long page = Page.NO_PAGE;
    
    protected FreeSpace next = null;
    protected FreeSpace previous = null;

    public FreeSpace(long pageNum, int space) {
        page = pageNum;
        free = space;
    }
    
    public int compareTo(FreeSpace other) {
        if (free < other.free)
            {return Constants.INFERIOR;}
        else if (free > other.free)
            {return Constants.SUPERIOR;}
        else
            {return Constants.EQUAL;}
	}
    
    public boolean equals(FreeSpace other) {
		return page == other.page;
	}
    
    /**
     * Returns the amount of unused space in the page (in bytes).
     * 
     * @return amount of unused space
     */
    public int getFree() {
        return free;
    }

    /**
     * The unique page number.
     * 
     * @return unique page number
     */
    public long getPage() {
        return page;
    }

    public void setFree(int space) {
        free = space;
    }
}
