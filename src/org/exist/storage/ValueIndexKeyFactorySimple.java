/* Created on 27 mai 2005
$Id$ */
package org.exist.storage;

import org.exist.EXistException;
import org.exist.util.ByteConversion;

/** Simple wrapper around an Indexable object, that adds the collectionId
 * to the srailization of the indexable.
 * TODO "ValueIndexKeyFactory" refactoring: use this class in NativeValueIndex */
public class ValueIndexKeyFactorySimple implements ValueIndexKeyFactory {

	private Indexable indexable;
	
	public ValueIndexKeyFactorySimple(Indexable indexable) {
		this.indexable = indexable;
	}

	/** called from {@link NativeValueIndex};
	 * provides the persistant storage key :
	 * (collectionId, qname, indexType, indexData) */
	public byte[] serialize(short collectionId, boolean caseSensitive)  throws EXistException {
        final byte[] data = indexable.serializeValue( 2, caseSensitive);
        ByteConversion.shortToByte(collectionId, data, 0);
		return data;
	}
	
	/** @return negative value <==> this object is less than other */
	public int compareTo(Object other) {
		int ret = 0;
		if ( other instanceof ValueIndexKeyFactorySimple ) {
			ValueIndexKeyFactorySimple otherIndexable = (ValueIndexKeyFactorySimple)other;
			ret = indexable.compareTo(otherIndexable.indexable);
		}
		return ret;
	}

}
