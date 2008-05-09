package org.exist.scheduler;

import java.util.Map;

import org.exist.storage.BrokerPool;

public class TestJob extends UserJavaJob {

    private String jobName = this.getClass().getName();
    
	public void execute(BrokerPool brokerpool, Map params) throws JobException {
		
		System.out.println("****** TEST JOB EXECUTED ******");

	}

	public String getName() {
		return jobName;
	}

    public void setName(String name) {
        this.jobName = name;
    }
}
