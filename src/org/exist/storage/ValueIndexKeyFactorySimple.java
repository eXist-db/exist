/* Created on 27 mai 2005
$Id$ */
package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.util.ByteConversion;
import org.exist.xquery.Constants;

/** Simple wrapper around an Indexable object, that adds the collectionId
 * to the srailization of the indexable.
 * TODO "ValueIndexKeyFactory" refactoring: use this class in NativeValueIndex */
public class ValueIndexKeyFactorySimple implements ValueIndexKeyFactory {
	
	public static int OFFSET_COLLECTION_ID = 0;
	public static int OFFSET_VALUE = OFFSET_COLLECTION_ID + Collection.LENGTH_COLLECTION_ID; //2

	private Indexable indexable;
	
	public ValueIndexKeyFactorySimple(Indexable indexable) {
		this.indexable = indexable;
	}

	/* provides the persistent storage key : collectionId + qname + indexType + indexData
	 * @deprecated
	 * @see org.exist.storage.ValueIndexKeyFactory#serialize(short, boolean)
	 */
	/*
	public byte[] serialize(short collectionId, boolean caseSensitive)  throws EXistException {
        final byte[] data = indexable.serializeValue(OFFSET_VALUE, caseSensitive);
        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
		return data;
	}
	*/

	/* provides the persistent storage key : collectionId + qname + indexType + indexData
	 * @see org.exist.storage.ValueIndexKeyFactory#serialize(short, boolean)
	 */
	public byte[] serialize(short collectionId)  throws EXistException {
        final byte[] data = indexable.serializeValue(OFFSET_VALUE);
        ByteConversion.shortToByte(collectionId, data, OFFSET_COLLECTION_ID);
		return data;
	}
	
	/** @return negative value <==> this object is less than other */
	public int compareTo(Object other) {
		int ret = Constants.EQUAL;
		if ( other instanceof ValueIndexKeyFactorySimple ) {
			ValueIndexKeyFactorySimple otherIndexable = (ValueIndexKeyFactorySimple)other;
			ret = indexable.compareTo(otherIndexable.indexable);
		}
		return ret;
	}

}
