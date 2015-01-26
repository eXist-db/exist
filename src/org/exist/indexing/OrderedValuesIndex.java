/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.indexing;

/**
 * Indexes that store their values in a determinist way (whatever it is) should implement this interface.
 * 
 * @author brihaye
 *
 */
public interface OrderedValuesIndex extends IndexWorker {
	
	
    /**
     * A key to the value "hint" to start from when the index scans its index entries
     */
    public static final String START_VALUE = "start_value";

    /**
     * A key to the value "hint" to end with when the index scans its index entries
     */
    public static final String END_VALUE = "end_value";

}
