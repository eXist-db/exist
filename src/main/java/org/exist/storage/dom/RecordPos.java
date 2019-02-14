/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 the eXist team
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
 * 
 */

package org.exist.storage.dom;

import org.exist.storage.dom.DOMFile.DOMPage;

public final class RecordPos {

    private DOMPage page;
    int offset;
    private short tupleID;
    private boolean isLink = false;

    public RecordPos(int offset, DOMPage page, short tupleID) {
        this.offset = offset;
        this.page = page;
        this.tupleID = tupleID;
    }

    public RecordPos(int offset, DOMPage page, short tupleID, boolean isLink) {
        this.offset = offset;
        this.page = page;
        this.tupleID = tupleID;
        this.isLink = isLink;
    }

    public DOMPage getPage() {
        return page;
    }

    public void setPage(DOMPage page) {
        this.page = page;
    }

    public short getTupleID() {
        return tupleID;
    }

    //Strange : only one call to this method
    public void setTupleID(short tupleID) {
        this.tupleID = tupleID;
    }

    public boolean isLink() {
        return isLink;
    }
}