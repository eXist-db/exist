package org.exist.scheduler;

import java.util.Map;

import org.exist.storage.BrokerPool;

public class TestJob extends UserJavaJob {

	public void execute(BrokerPool brokerpool, Map params) throws JobException {
		
		System.out.println("****** TEST JOB EXECUTED ******");

	}

	public String getName() {
		return this.getClass().getName();
	}

}
