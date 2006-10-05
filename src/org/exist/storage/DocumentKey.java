/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  $Id$
 */
package org.exist.storage;

import org.exist.storage.btree.Value;
import org.exist.util.ByteConversion;

public class DocumentKey extends Value {

    public DocumentKey(short collectionId) {
        data = new byte[2];
        ByteConversion.shortToByte(collectionId, data, 0);
        len = 2;
        pos = 0;
    }
    
    public DocumentKey(short collectionId, byte type, int docId) {
        data = new byte[7];
        ByteConversion.shortToByte(collectionId, data, 0);
        data[2] = type;
        ByteConversion.intToByte(docId, data, 3);
        len = 7;
        pos = 0;
    }
}