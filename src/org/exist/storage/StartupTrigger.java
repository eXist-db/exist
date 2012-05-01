package org.exist.storage;

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
     * If you want an Asynchronous Trigger, simply use a Future in your implementation
     */
    public void execute(final DBBroker broker);
}
