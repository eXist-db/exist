package org.exist.util;

/**
 * CascadingThrowable.java
 * 
 * @author Wolfgang Meier
 */
public interface CascadingThrowable {

    /**
     * Returns the root cause of this throwable.
     * 
     * @return Throwable
     */
    Throwable getCause();
}
