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

import org.exist.EXistException;

/**
 * This interface should be implemented by all basic types
 * to be used as keys in a value index.
 * 
 * @see org.exist.storage.NativeValueIndex
 * @author wolf
 */
public interface Indexable extends Comparable {
    
    /**
     * Serialize the value plus collection and possibly element information
     * to an array of bytes.

     * The returned byte array has the following format:
     * 
     * (short: collectionId, byte type, byte[] value)
     * 
     * @param collectionId the collection id to use
     */
	//TODO : better exception ?
    //public byte[] serialize(short collectionId) throws EXistException;

    /**
     * Serialize the value plus collection and possibly element information
     * to an array of bytes.
     * @ deprecated use following function instead; this API should be local
     * to value index class like {@link NativeValueIndex}
     * 
     * The returned byte array has the following format:
     * 
     * (short: collectionId, byte type, byte[] value)
     * 
     * @param collectionId the collection id to use
     * @param caseSensitive only relevant for string values: if set to false,
     * strings should be serialized in lower case
     */
	//TODO : better exception ?
    //public byte[] serialize(short collectionId, boolean caseSensitive) throws EXistException;

    /** Serialize the value to an array of bytes for the persistant storage.
     * 
     * The returned byte array has the following format:
     * 
     * (offset-1 free bytes, byte type, byte[] value)
     * 
     * @ deprecated use following function instead; this API should be local
     * to value index class like {@link NativeValueIndex}
     * @param offset starting index for writing in array data
     * @return the size actually writen in the array argument
     */
    //TODO : better exception ?
	//public byte[] serializeValue(int offset, boolean caseSensitive) throws EXistException;

    /** Serialize the value to an array of bytes for the persistant storage.
     * 
     * The returned byte array has the following format:
     * 
     * (offset-1 free bytes, byte type, byte[] value)
     * 
     * @param offset starting index for writing in array data
     * @return the size actually writen in the array argument
     * @throws EXistException in reponse to an eXist-db error
     */
    //TODO : better exception ?
	public byte[] serializeValue(int offset) throws EXistException;
	
    /**
     * Returns the type of the Indexable as one of the constants defined
     * in {@link org.exist.xquery.value.Type}.
     * 
     * @return Type of the Indexable
     */
    int getType();
}
