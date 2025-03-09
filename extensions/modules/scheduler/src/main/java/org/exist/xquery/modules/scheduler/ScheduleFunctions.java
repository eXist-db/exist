/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.scheduler;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.exist.dom.QName;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.UserJavaJob;
import org.exist.scheduler.UserJob;
import org.exist.scheduler.UserXQueryJob;
import org.exist.security.Subject;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.util.Properties;


/**
 * eXist Scheduler Module Extension ScheduleFunctions.
 *
 * Schedules job's with eXist's Scheduler
 *
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @author <a href="mailto:loren.cahlander@gmail.com">Loren Cahlander</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @version  1.3
 * @see      org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 * @serial   2010-03-10
 */
public class ScheduleFunctions extends BasicFunction
{
    public static final String              SCHEDULE_XQUERY_CRON_JOB     = "schedule-xquery-cron-job";

    public static final String              SCHEDULE_XQUERY_PERIODIC_JOB = "schedule-xquery-periodic-job";

    public static final String              SCHEDULE_JAVA_CRON_JOB       = "schedule-java-cron-job";

    public static final String              SCHEDULE_JAVA_PERIODIC_JOB   = "schedule-java-periodic-job";

   private final static FunctionSignature scheduleJavaCronJobNoParam = new FunctionSignature(
			new QName( SCHEDULE_JAVA_CRON_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
			"Schedules the Java Class named (the class must extend org.exist.scheduler.UserJavaJob) according " +
            "to the Cron expression. The job will be registered using the job name.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "java-classname", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the class to be executed.  It must extend the org.exist.scheduler.UserJavaJob class." ),
				new FunctionParameterSequenceType( "cron-expression", Type.STRING, Cardinality.EXACTLY_ONE, "The cron expression.  Please see the scheduler documentation." ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." )
			},
			new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "a flag indicating successful execution" )
		);
	
	private final static FunctionSignature scheduleJavaCronJobParam = new FunctionSignature(
            new QName( SCHEDULE_JAVA_CRON_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
            "Schedules the Java Class named (the class must extend org.exist.scheduler.UserJavaJob) according " +
            "to the Cron expression. The job will be registered using the name passed in $job-name. The final " +
            "argument can be used to specify " +
            "parameters for the job, which will be passed to the query as external variables. Parameters are specified " +
            "in an XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "java-classname", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the class to be executed.  It must extend the org.exist.scheduler.UserJavaJob class." ),
				new FunctionParameterSequenceType( "cron-expression", Type.STRING, Cardinality.EXACTLY_ONE, "The cron expression.  Please see the scheduler documentation." ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." ),
                new FunctionParameterSequenceType( "job-parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" )
            },
            new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "a flag indicating successful execution" )
		);
	
	private final static FunctionSignature scheduleJavaPeriodicParam = new FunctionSignature(
            new QName( SCHEDULE_JAVA_PERIODIC_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
            "Schedules the Java Class named (the class must extend org.exist.scheduler.UserJavaJob) according " +
            "to the periodic value. The job will be registered using the job name. The $job-parameters " +
            "argument can be used to specify " +
            "parameters for the job, which will be passed to the query as external variables. Parameters are specified " +
            "in an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>,  Given the delay and the repeat.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "java-classname", Type.STRING, Cardinality.EXACTLY_ONE, "The full name of the class to be executed.  It must extend the org.exist.scheduler.UserJavaJob class." ),
                new FunctionParameterSequenceType( "period", Type.INTEGER, Cardinality.EXACTLY_ONE, "Time in milliseconds between execution of the job" ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." ),
                new FunctionParameterSequenceType( "job-parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" ),
                new FunctionParameterSequenceType( "delay", Type.INTEGER, Cardinality.EXACTLY_ONE, "The period in milliseconds to delay the start of a job." ),
                new FunctionParameterSequenceType( "repeat", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of times to repeat the job after the initial execution. A value of -1 means repeat forever." )
            },
            new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "a flag indicating successful execution" )
		);
	
	private final static FunctionSignature scheduleXQueryCronJobNoParam = new FunctionSignature(
			new QName( SCHEDULE_XQUERY_CRON_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
			"Schedules the named XQuery resource (e.g. /db/foo.xql) according to the Cron expression. " +
			"XQuery job's will be launched under the guest account initially, although the running XQuery may switch permissions through calls to xmldb:login(). " +
            "The job will be registered using the job name. " +
            "Jobs submitted via this function are transitory and will be lost on a server restart. To ensure the persistence of scheduled tasks add them to the conf.xml file.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "xquery-resource", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the XQuery resource" ),
				new FunctionParameterSequenceType( "cron-expression", Type.STRING, Cardinality.EXACTLY_ONE, "The cron expression.  Please see the scheduler documentation." ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." )
			},
			new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "a flag indicating successful execution" )
		);
	
	private final static FunctionSignature scheduleXQueryCronJobParam = new FunctionSignature(
            new QName( SCHEDULE_XQUERY_CRON_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
            "Schedules the named XQuery resource (e.g. /db/foo.xql) according to the Cron expression. " +
			"XQuery job's will be launched under the guest account initially, although the running XQuery may switch permissions through calls to xmldb:login(). " +
            "The job will be registered using the job name. The final argument can be used to specify " +
            "parameters for the job, which will be passed to the query as external variables. Parameters are specified " +
            "in an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters> " +
            "Jobs submitted via this function are transitory and will be lost on a server restart. To ensure the persistence of scheduled tasks add them to the conf.xml file.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "xquery-resource", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the XQuery resource" ),
				new FunctionParameterSequenceType( "cron-expression", Type.STRING, Cardinality.EXACTLY_ONE, "A cron expression.  Please see the scheduler documentation." ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." ),
                new FunctionParameterSequenceType( "job-parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" )
            },
            new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Flag indicating successful execution" )
		);
	
	private final static FunctionSignature scheduleXQueryCronJobParamException = new FunctionSignature(
            new QName( SCHEDULE_XQUERY_CRON_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
            "Schedules the named XQuery resource (e.g. /db/foo.xql) according to the Cron expression. " +
			"XQuery job's will be launched under the guest account initially, although the running XQuery may switch permissions through calls to xmldb:login(). " +
            "The job will be registered using the job name. The job parameters argument can be used to specify " +
            "parameters for the job, which will be passed to the query as external variables. Parameters are specified " +
            "in an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters> " +
            "Jobs submitted via this function are transitory and will be lost on a server restart. To ensure the persistence of scheduled tasks add them to the conf.xml file.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "xquery-resource", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the XQuery resource" ),
				new FunctionParameterSequenceType( "cron-expression", Type.STRING, Cardinality.EXACTLY_ONE, "A cron expression.  Please see the scheduler documentation." ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." ),
                new FunctionParameterSequenceType( "job-parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" ),
				new FunctionParameterSequenceType( "unschedule", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Specifies whether to unschedule this job if an XPathException is raised, default is true." )
            },
            new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Flag indicating successful execution" )
		);
	
	private final static FunctionSignature scheduleXQueryPeriodicParam = new FunctionSignature(
            new QName( SCHEDULE_XQUERY_PERIODIC_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
            "Schedules the named XQuery resource (e.g. /db/foo.xql) according to the period. " +
			"XQuery job's will be launched under the guest account initially, although the running XQuery may switch permissions through calls to xmldb:login(). " +
            "The job will be registered using the job name. The job parameters argument can be used to specify " +
            "parameters for the job, which will be passed to the query as external variables. Parameters are specified " +
            "in an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" +
			",  Given the delay passed and the repeat value. " +
            "Jobs submitted via this function are transitory and will be lost on a server restart. To ensure the persistence of scheduled tasks add them to the conf.xml file.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "xquery-resource", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the XQuery resource" ),
                new FunctionParameterSequenceType( "period", Type.INTEGER, Cardinality.EXACTLY_ONE, "Time in milliseconds between execution of the job" ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." ),
                new FunctionParameterSequenceType( "job-parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" ),
                new FunctionParameterSequenceType( "delay", Type.INTEGER, Cardinality.EXACTLY_ONE, "Can be used with a period in milliseconds to delay the start of a job." ),
                new FunctionParameterSequenceType( "repeat", Type.INTEGER, Cardinality.EXACTLY_ONE, "Number of times to repeat the job after the initial execution. A value of -1 means repeat forever." )
            },
            new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Flag indicating successful execution" )
		);

	private final static FunctionSignature scheduleXQueryPeriodicParamException = new FunctionSignature(
            new QName( SCHEDULE_XQUERY_PERIODIC_JOB, SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
            "Schedules the named XQuery resource (e.g. /db/foo.xql) according to the period. " +
			"XQuery job's will be launched under the guest account initially, although the running XQuery may switch permissions through calls to xmldb:login(). " +
            "The job will be registered using the job name. The job parameters argument can be used to specify " +
            "parameters for the job, which will be passed to the query as external variables. Parameters are specified " +
            "in an XML fragment with the following structure: " +
			"<parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" +
			",  Given the delay passed and the repeat value. " + 
            "Jobs submitted via this function are transitory and will be lost on a server restart. To ensure the persistence of scheduled tasks add them to the conf.xml file.",
            new SequenceType[]
            {
				new FunctionParameterSequenceType( "xquery-resource", Type.STRING, Cardinality.EXACTLY_ONE, "The path to the XQuery resource" ),
                new FunctionParameterSequenceType( "period", Type.INTEGER, Cardinality.EXACTLY_ONE, "Time in milliseconds between execution of the job" ),
                new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job." ),
                new FunctionParameterSequenceType( "job-parameters", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "XML fragment with the following structure: <parameters><param name=\"param-name1\" value=\"param-value1\"/></parameters>" ),
                new FunctionParameterSequenceType( "delay", Type.INTEGER, Cardinality.EXACTLY_ONE, "Can be used with a period in milliseconds to delay the start of a job." ),
                new FunctionParameterSequenceType( "repeat", Type.INTEGER, Cardinality.EXACTLY_ONE, "Number of times to repeat the job after the initial execution. A value of -1 means repeat forever." ),
				new FunctionParameterSequenceType( "unschedule", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Specifies whether to unschedule this job if an XPathException is raised, default is true." )
            },
            new FunctionParameterSequenceType( "success", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Flag indicating successful execution" )
		);
	
	public final static FunctionSignature[] signatures = {
			scheduleJavaCronJobNoParam, 
			scheduleJavaCronJobParam,
			scheduleJavaPeriodicParam, 
			scheduleXQueryCronJobNoParam,
			scheduleXQueryCronJobParam, 
			scheduleXQueryCronJobParamException,
			scheduleXQueryPeriodicParam,
			scheduleXQueryPeriodicParamException
	};

    private Scheduler                       scheduler                    = null;

    /**
     * ScheduleFunctions Constructor.
     *
     * @param  context    The Context of the calling XQuery
     * @param  signature  DOCUMENT ME!
     */
    public ScheduleFunctions( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );

        scheduler = context.getBroker().getBrokerPool().getScheduler();
    }

    /**
     * evaluate thed call to the xquery function, it is really the main entry point of this class.
     *
     * @param   args             arguments from the function call
     * @param   contextSequence  the Context Sequence to operate on (not used here internally!)
     *
     * @return  A sequence representing the result of the function call
     *
     * @throws  XPathException  DOCUMENT ME!
     *
     * @see     org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        String     resource      = args[0].getStringValue();
		boolean	   unschedule	 = true;
        long       periodicValue = 0;
        long       delayValue    = 0;
        int        repeatValue   = -1;
        String     jobName       = args[2].getStringValue();
        Properties properties    = null;
		
		boolean	   isPeriodic	= isCalledAs( SCHEDULE_XQUERY_PERIODIC_JOB ) || isCalledAs( SCHEDULE_JAVA_PERIODIC_JOB );

        if( ( getArgumentCount() >= 4 ) && args[3].hasOne() ) {
            Node options = ( ( NodeValue )args[3].itemAt( 0 ) ).getNode();
            properties = new Properties();
            parseParameters( options, properties );
        }

        if( isPeriodic && getArgumentCount() >= 5 ) {
            delayValue = ( ( IntegerValue )args[4].itemAt( 0 ) ).getLong();
        }

        if( isPeriodic && getArgumentCount() >= 6 ) {
            repeatValue = ( ( IntegerValue )args[5].itemAt( 0 ) ).getInt();
        }
		
        Subject user = context.getSubject();

        //Check if the user is a DBA
        if( !user.hasDbaRole() ) {
            return( BooleanValue.FALSE );
        }

        Object  job    = null;
        boolean isCron = true;

        //scheule-xquery-cron-job
        if( isCalledAs( SCHEDULE_XQUERY_CRON_JOB ) ) {
			if( getArgumentCount() >= 5 ) {
				unschedule	 = args[4].effectiveBooleanValue();
			}
            job = new UserXQueryJob( jobName, resource, user );
        } else if( isCalledAs( SCHEDULE_XQUERY_PERIODIC_JOB ) ) {
			if( getArgumentCount() >= 7 ) {
				unschedule	 = args[6].effectiveBooleanValue();
			}
            periodicValue = ( ( IntegerValue )args[1].itemAt( 0 ) ).getLong();
            job           = new UserXQueryJob( jobName, resource, user );
            isCron        = false;
        }

        //schedule-java-cron-job
        else if( isCalledAs( SCHEDULE_JAVA_CRON_JOB ) || isCalledAs( SCHEDULE_JAVA_PERIODIC_JOB ) ) {

            if( isCalledAs( SCHEDULE_JAVA_PERIODIC_JOB ) ) {
                periodicValue = ( ( IntegerValue )args[1].itemAt( 0 ) ).getLong();
                isCron = false;
            }

            try {

                //Check if the Class is a UserJob
                Class<?> jobClass = Class.forName( resource );
                job = jobClass.newInstance();

                if( !( job instanceof UserJavaJob ) ) {
                    LOG.error("Cannot Schedule job. Class {} is not an instance of org.exist.scheduler.UserJavaJob", resource);
                    return( BooleanValue.FALSE );
                }
                ( ( UserJavaJob )job ).setName( jobName );
            }
            catch( ClassNotFoundException | InstantiationException | IllegalAccessException cnfe ) {
                LOG.error( cnfe );
                return( BooleanValue.FALSE );
            }
        }

        if( job != null ) {

            if( isCron ) {

                //schedule the job
                String cronExpression = args[1].getStringValue();

                if( scheduler.createCronJob( cronExpression, ( UserJob )job, properties, unschedule ) ) {
                    return( BooleanValue.TRUE );
                } else {
                    return( BooleanValue.FALSE );
                }
            } else {

                //schedule the job
                if( scheduler.createPeriodicJob( periodicValue, ( UserJob )job, delayValue, properties, repeatValue, unschedule ) ) {
                    return( BooleanValue.TRUE );
                } else {
                    return( BooleanValue.FALSE );
                }
            }
        } else {
            return( BooleanValue.FALSE );
        }
    }


    private void parseParameters( Node options, Properties properties ) throws XPathException
    {
        if( ( options.getNodeType() == Node.ELEMENT_NODE ) && "parameters".equals(options.getLocalName()) ) {
            Node child = options.getFirstChild();

            while( child != null ) {

                if( ( child.getNodeType() == Node.ELEMENT_NODE ) && "param".equals(child.getLocalName()) ) {
                    Element elem  = ( Element )child;
                    String  name  = elem.getAttribute( "name" );
                    String  value = elem.getAttribute( "value" );

                    if( ( name == null ) || ( value == null ) ) {
                        throw( new XPathException( this, "Name or value attribute missing for stylesheet parameter" ) );
                    }
                    properties.setProperty( name, value );
                }
                child = child.getNextSibling();
            }
        }
    }
}
