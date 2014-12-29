/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.indexing.range.conversion;

import org.apache.lucene.document.Field;
import org.apache.lucene.util.BytesRef;
import org.exist.xquery.value.AtomicValue;

/**
 * Interface for on-the-fly type conversion when populating an index.
 */
public interface TypeConverter {

    /**
     * All content to be indexed will be passed to this method. It should
     * return a Lucene field with a type appropriate for the particular content.
     *
     * @param fieldName name of the field being indexed
     * @param content the content to be written to the index
     * @return a lucene field to be added to the document
     */
    public Field toField(String fieldName, String content);
}
