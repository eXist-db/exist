/* Created on 27 mai 2005
$Id$ */
package org.exist.storage;


/** Factory for Keys for Value Indices;
 * provides through serialize() the persistant storage key. */
public interface ValueIndexKeyFactory extends Comparable {

	/** this is called from {@link NativeValueIndex} 
	 * @return the persistant storage key */
	//public byte[] serialize(short collectionId, boolean caseSensitive) throws EXistException;
}
