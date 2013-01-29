package org.exist.storage;

import java.util.List;
import java.util.Map;

/**
 * Database Startup Trigger
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public interface StartupTrigger {
    
    /**
     * Synchronously execute a task at database Startup before the database is made available to connections
     * 
     * Any RuntimeExceptions thrown will be ignored and database startup will continue
     * Database Startup cannot be aborted by this Trigger!
     * 
     * Note, If you want an Asynchronous Trigger, simply use a Future in your implementation
     * 
     * @param broker
     * @param params Key, Values
     */
    public void execute(final DBBroker sysBroker, final Map<String, List<? extends Object>> params);
}
