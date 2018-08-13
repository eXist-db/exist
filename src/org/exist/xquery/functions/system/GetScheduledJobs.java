/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.functions.system;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.scheduler.ScheduledJobInfo;
import org.exist.scheduler.ScheduledJobInfo.TriggerState;
import org.exist.storage.BrokerPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class GetScheduledJobs extends BasicFunction {

    protected final static Logger logger = LogManager.getLogger(GetScheduledJobs.class);

    private static final String TODAY_TIMESTAMP				= "HH:mm:ss.SSS Z";
	private static final String DATE_TIME_FORMAT 			= "yyyy-MM-dd HH:mm:ss.SSS Z";
	final static String NAMESPACE_URI                       = SystemModule.NAMESPACE_URI;
    final static String PREFIX                              = SystemModule.PREFIX;

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName( "get-scheduled-jobs", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Get a list of scheduled jobs (dba role only).",
                null,
                new FunctionReturnSequenceType( Type.ITEM, Cardinality.EXACTLY_ONE, "a node containing the list of scheduled jobs" )
        );

    public GetScheduledJobs (XQueryContext context ) 
	{
        super( context, signature );
    }

    @Override
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
        if( !context.getSubject().hasDbaRole() ) {
            final XPathException xPathException = new XPathException( this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to get the list of scheduled jobs" );
        	logger.error("Invalid user " + SystemModule.PREFIX + ":get-scheduled-jobs", xPathException);
			throw xPathException;
        }

        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement( new QName( "jobs", NAMESPACE_URI, PREFIX ), null );

        final BrokerPool brokerPool = context.getBroker().getBrokerPool();
        logger.trace("brokerPool = " + brokerPool.toString());
        
        if( brokerPool != null ) {
            final org.exist.scheduler.Scheduler existScheduler = brokerPool.getScheduler();
            
            if( existScheduler != null ) {
                final List<ScheduledJobInfo> scheduledJobsInfo = existScheduler.getScheduledJobs();
                final ScheduledJobInfo[] executingJobsInfo = existScheduler.getExecutingJobs();
                
                if( scheduledJobsInfo != null ) {
                    for(final ScheduledJobInfo scheduledJobInfo : scheduledJobsInfo) {
                    	addRow(scheduledJobInfo, builder, false );
                    }
                }
                if( executingJobsInfo != null ) {
                    for (ScheduledJobInfo jobInfo : executingJobsInfo) {
                        addRow(jobInfo, builder, true);
                    }
                }
            }
        }

        builder.endElement();
        builder.endDocument();

        return( (NodeValue)builder.getDocument().getDocumentElement() );
    }
    
    private void addRow(ScheduledJobInfo scheduledJobInfo, MemTreeBuilder builder, boolean isRunning) {
    	logger.trace("Entring addRow");
    	
        final String name = scheduledJobInfo.getName();
    	final String group = scheduledJobInfo.getGroup();
    	final String triggerName = scheduledJobInfo.getTriggerName();
    	final Date startTime = scheduledJobInfo.getStartTime();
    	final Date endTime = scheduledJobInfo.getEndTime();
    	final Date fireTime = scheduledJobInfo.getPreviousFireTime();
    	final Date nextFireTime = scheduledJobInfo.getNextFireTime();
    	final Date finalFireTime = scheduledJobInfo.getFinalFireTime();
    	final String triggerExpression = scheduledJobInfo.getTriggerExpression();
    	final TriggerState triggerState = scheduledJobInfo.getTriggerState();
		
        builder.startElement(new QName("job", NAMESPACE_URI, PREFIX), null);
        builder.addAttribute(new QName("name", null, null), name);
        builder.addAttribute(new QName("group", null, null), group);
        builder.addAttribute(new QName("triggerName", null, null), triggerName) ;
        builder.addAttribute(new QName("startTime", null, null), dateText(startTime));
        builder.addAttribute(new QName("endTime", null, null), dateText(endTime));
        builder.addAttribute(new QName("fireTime", null, null), dateText(fireTime));
        builder.addAttribute(new QName("nextFireTime", null, null), dateText(nextFireTime));
        builder.addAttribute(new QName("finalFireTime", null, null), dateText(finalFireTime));
        builder.addAttribute(new QName("triggerExpression", null, null), triggerExpression);
        builder.addAttribute(new QName("triggerState", null, null), triggerState.name());
        builder.addAttribute(new QName("running", null, null), (isRunning) ? "RUNNING" : "SCHEDULED");
        builder.endElement();
        logger.trace("Exiting addRow");
    }
    
    private String dateText( Date aDate ) 
	{ 	
    	String returnValue = "";
		
    	if( aDate != null ) {
    		String formatString = DATE_TIME_FORMAT;

    		if( isToday( aDate ) ) {
    			formatString = TODAY_TIMESTAMP;
    		}
    		final SimpleDateFormat format = new SimpleDateFormat( formatString );
			
    		returnValue = format.format(aDate);
    	}
		
    	return( returnValue );
    }

    private boolean isToday( Date aDate )
    {
        final Calendar aCal1 = Calendar.getInstance();
        aCal1.setTime( aDate );

        final Calendar aCal2 = Calendar.getInstance();

        return aCal1.get(Calendar.DATE) == aCal2.get(Calendar.DATE) &&
                aCal1.get(Calendar.YEAR) == aCal2.get(Calendar.YEAR) &&
                aCal1.get(Calendar.MONTH) == aCal2.get(Calendar.MONTH);
    }

}
