package org.exist.util;

/**
 * @author wolf
 */
public interface Lockable {

    /**
     * Get the name of the Lock.
     *
     * @return the name of the Lock
     */
    String getLockName();
    
}
