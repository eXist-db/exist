/*
 * CollectionStore.java - Jun 19, 2003
 * 
 * @author wolf
 */
package org.exist.storage.store;

import java.io.File;

import org.exist.collections.CollectionCache;

public class CollectionStore extends BFile {

	//	size of the internal buffer for collection objects
	public static final int COLLECTION_BUFFER_SIZE = 128;
	
	private CollectionCache collectionsCache = new CollectionCache(COLLECTION_BUFFER_SIZE);
	
	/**
	 * 
	 */
	public CollectionStore() {
		super();
	}

	/**
	 * @param file
	 */
	public CollectionStore(File file) {
		super(file);
	}

	/**
	 * @param file
	 * @param buffers
	 */
	public CollectionStore(File file, int buffers) {
		super(file, buffers);
	}

	/**
	 * @param file
	 * @param btreeBuffers
	 * @param dataBuffers
	 */
	public CollectionStore(File file, int btreeBuffers, int dataBuffers) {
		super(file, btreeBuffers, dataBuffers);
	}

	public CollectionCache getCollectionCache() {
		return collectionsCache;
	}
}
