package org.exist.storage;

import org.exist.storage.txn.Txn;

import java.util.List;
import java.util.Map;

/**
 * Database Startup Trigger
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public interface StartupTrigger {
    
    /**
     * Synchronously execute a task at database Startup before the database is made available to connections
     * Remember, your code within the execute function will block the database startup until it completes!
     *
     * Any RuntimeExceptions thrown will be ignored and database startup will continue
     * Database Startup cannot be aborted by this Trigger!
     * 
     * Note: If you want an Asynchronous Trigger, you could use a Future in your implementation
     * to start a new thread, however you cannot access the sysBroker from that thread
     * as it may have been returned to the broker pool. Instead if you need a broker, you may be able to
     * do something clever by checking the database status and then acquiring a new broker
     * from the broker pool. If you wish to work with the broker pool you must obtain this before
     * starting your asynchronous execution by calling sysBroker.getBrokerPool().
     * 
     * @param sysBroker The single system broker available during database startup
     * @param transaction Transaction
     * @param params Key, Values
     */
    public void execute(final DBBroker sysBroker, final Txn transaction, final Map<String, List<? extends Object>> params);
}
