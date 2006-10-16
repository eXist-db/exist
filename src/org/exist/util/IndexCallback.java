/*
 * IndexCallback.java - Mar 12, 2003
 * 
 * @author wolf
 */
package org.exist.util;

import org.exist.storage.btree.Value;

public interface IndexCallback {

	public boolean indexInfo(Value key, Value value);
}
