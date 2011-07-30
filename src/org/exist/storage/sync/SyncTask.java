package org.exist.storage.sync;

import java.util.Properties;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.util.Configuration;

public class SyncTask implements SystemTask {

	@SuppressWarnings("unused")
	private final static String JOB_GROUP = "eXist.internal";
	private final static String JOB_NAME = "Sync";
	
	public static String getJobName() {
		return JOB_NAME;
	}
	
	public static String getJobGroup() {
		return JOB_GROUP;
	}
	
	@Override
	public boolean afterCheckpoint() {
		// a checkpoint is created by the MAJOR_SYNC event
		return false;
	}
	
	@Override
	public void configure(Configuration config, Properties properties)
			throws EXistException {
	}

	@Override
	public void execute(DBBroker broker) throws EXistException {
		BrokerPool pool = broker.getBrokerPool();
		if(System.currentTimeMillis() - pool.getLastMajorSync() > pool.getMajorSyncPeriod())
		{
			pool.sync(broker, Sync.MAJOR_SYNC);
		}
		else
		{
			pool.sync(broker, Sync.MINOR_SYNC);
		}
	}
}
