package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.security.User;
import org.exist.storage.sync.Sync;

import java.util.Stack;

public class SystemTaskManager {

    private final static Logger LOG = Logger.getLogger(SystemTaskManager.class);

    /**
	 * The pending system maintenance tasks of the database instance.
	 */
	private final Stack waitingSystemTasks = new Stack();

    private BrokerPool pool;
    
    public SystemTaskManager(BrokerPool pool) {
        this.pool = pool;
    }

    public void triggerSystemTask(SystemTask task) {
        synchronized (waitingSystemTasks) {
            waitingSystemTasks.push(task);
            pool.getTransactionManager().processSystemTasks();
        }
    }

    public void processTasks() {
        //dont run the task if we are shutting down
    	if(pool.isShuttingDown())
    		return;
        synchronized (waitingSystemTasks) {
            DBBroker broker = null;
            User oldUser = null;
    	    try {
                broker = pool.get(null);
                oldUser = broker.getUser();
                broker.setUser(org.exist.security.SecurityManager.SYSTEM_USER);
                while (!waitingSystemTasks.isEmpty()) {
                	SystemTask task = (SystemTask) waitingSystemTasks.pop();
                	if (task.afterCheckpoint())
                		pool.sync(broker, Sync.MAJOR_SYNC);
                    runSystemTask(task, broker);
                }
            } catch(Exception e) {
                LOG.warn("System maintenance task reported error: " + e.getMessage(), e);
            } finally {
                if (oldUser != null)
                    broker.setUser(oldUser);
                pool.release(broker);
            }
        }
    }

    private void runSystemTask(SystemTask task, DBBroker broker) throws EXistException {
        if (LOG.isDebugEnabled())
            LOG.debug("Running system maintenance task: " + task.getClass().getName());
        task.execute(broker);
        if (LOG.isDebugEnabled())
            LOG.debug("System task completed.");
    }

    public void initialize() {
        waitingSystemTasks.clear();
    }

    public void shutdown() {
        synchronized (waitingSystemTasks) {
            waitingSystemTasks.clear();
        }
    }
}