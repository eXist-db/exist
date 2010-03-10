package org.exist.scheduler;

import org.exist.storage.BrokerPool;

import java.util.Map;


public class TestJob extends UserJavaJob
{
    private String jobName = this.getClass().getName();

    public void execute( BrokerPool brokerpool, Map<String, ?> params ) throws JobException
    {
        System.out.println( "****** TEST JOB EXECUTED ******" );

    }


    public String getName()
    {
        return( jobName );
    }


    public void setName( String name )
    {
        this.jobName = name;
    }
}
