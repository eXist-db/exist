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
