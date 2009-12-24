package org.exist.xmldb;

import java.util.Timer;
import java.util.TimerTask;

import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Local implementation of the DatabaseInstanceManager.
 */
public class LocalDatabaseInstanceManager implements DatabaseInstanceManager {

	protected BrokerPool pool;
	protected User user;
	
	public LocalDatabaseInstanceManager(User user, BrokerPool pool) {
		this.pool = pool;
		this.user = user;
	}
	
	public void shutdown() throws XMLDBException {
		shutdown(0);
	}

	public void shutdown(long delay) throws XMLDBException {
		if(!user.hasDbaRole())
			throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, 
				"only users in group dba may " +				
				"shut down the database");
		if(delay > 0) {
			TimerTask task = new TimerTask() {
				public void run() {
					pool.shutdown();
				}
			};
			Timer timer = new Timer();
			timer.schedule(task, delay);
		} else
			pool.shutdown();
	}


    public boolean enterServiceMode() throws XMLDBException {
        try {
            pool.enterServiceMode(user);
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
        return true;
    }

    public void exitServiceMode() throws XMLDBException {
        try {
            pool.exitServiceMode(user);
        } catch (PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        }
    }

    public DatabaseStatus getStatus() throws XMLDBException {
		return new DatabaseStatus(pool);
	}

	/**
	 * @see org.xmldb.api.base.Service#getName()
	 */
	public String getName() throws XMLDBException {
		return "DatabaseInstanceManager";
	}

	/**
	 * @see org.xmldb.api.base.Service#getVersion()
	 */
	public String getVersion() throws XMLDBException {
		return "1.0";
	}

	public boolean isLocalInstance() {
		return true;
	}
	
	/**
	 * @see org.xmldb.api.base.Service#setCollection(org.xmldb.api.base.Collection)
	 */
	public void setCollection(Collection arg0) throws XMLDBException {
	}

	/**
	 * @see org.xmldb.api.base.Configurable#getProperty(java.lang.String)
	 */
	public String getProperty(String arg0) throws XMLDBException {
		return null;
	}

	/**
	 * @see org.xmldb.api.base.Configurable#setProperty(java.lang.String, java.lang.String)
	 */
	public void setProperty(String arg0, String arg1) throws XMLDBException {
	}

	public boolean isXACMLEnabled() throws XMLDBException {
		return pool.getSecurityManager().isXACMLEnabled();
	}

}
