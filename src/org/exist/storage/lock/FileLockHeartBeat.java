package org.exist.storage.lock;

import java.io.IOException;
import java.util.Map;

import org.exist.scheduler.JobException;
import org.exist.scheduler.SystemJob;
import org.exist.storage.BrokerPool;

/**
 * Provides a Scheduled HeartBeat for the FileLock
 */
public class FileLockHeartBeat extends SystemJob
{
	private String JOB_NAME = "FileLockHeartBeat";
	
	public FileLockHeartBeat()
	{
	}
	
	public FileLockHeartBeat(String lockName)
	{
		JOB_NAME += ": " + lockName;
	}
	
	public String getName()
	{
		return JOB_NAME;
	}
	
	public void execute(BrokerPool pool, Map params) throws JobException
	{
		//get the file lock
		FileLock lock = (FileLock)params.get(FileLock.class.getName());
		
		if(lock != null)
		{
			try
			{
				lock.save();
	        }
			catch(IOException e)
			{
	            lock.message("Caught exception while trying to write lock file", e);
	        }	
		}
		else
		{
			//abort this job
			throw new JobException(JobException.JOB_ABORT_THIS, "Unable to Heart Beat, FileLock was null!");
		}
	} 
}
