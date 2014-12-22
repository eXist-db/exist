/*
 *  eXist Scheduler Module Extension GetSheduledJobs
 *  Copyright (C) 2006-09 Adam Retter <adam.retter@devon.gov.uk>
 *  www.adamretter.co.uk
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.modules.scheduler;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import org.exist.dom.QName;
import org.exist.scheduler.ScheduledJobInfo;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.UserJob;
import org.exist.security.Subject;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;


/**
 * eXist Scheduler Module Extension GetScheduledJobs.
 *
 * <p>Retrieves details of Jobs that have been Scheduled</p>
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Loren Cahlander <loren@syntactica.com>
 * @version  1.3
 * @see      org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 * @serial   2007-12-04
 * @serial   2009-07-09
 */
public class GetScheduledJobs extends BasicFunction
{
    	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "get-scheduled-jobs", SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
			"Gets the details of all scheduled jobs in the form: " +
			"<scheduler:jobs xmlns:scheduler=\"http://exist-db.org/xquery/scheduler\" count=\"iJobs\">" +
			"    <scheduler:group name=\"group\">" +
			"        <scheduler:job name=\"\">" +
			"            <scheduler:trigger name=\"\">" +
			"                <expression></expression>" +
			"                <state></state>" +
			"                <start></start>" +
			"                <end></end>" +
			"                <previous></previous>" +
			"                <next></next>" +
			"                <final></final>" +
			"            </scheduler:trigger>" +
			"        </scheduler:job>" +
			"    </scheduler:group>" +
			"</scheduler:jobs>",
			null,
			new FunctionReturnSequenceType( Type.NODE, Cardinality.EXACTLY_ONE, "the XML containing the list of jobs" ) 
		);
	
	private Scheduler                     scheduler = null;

    /**
     * GetScheduledJobs Constructor.
     *
     * @param  context    The Context of the calling XQuery
     * @param  signature  DOCUMENT ME!
     */
    public GetScheduledJobs( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );

        scheduler = context.getBroker().getBrokerPool().getScheduler();
    }

    /**
     * evaluate the call to the xquery function, it is really the main entry point of this class.
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
    @Override
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        Subject               user           = context.getUser();

        boolean            userhasDBARole = user.hasDbaRole();

        StringBuilder      xmlBuf         = new StringBuilder();

        int                iJobs          = 0;
        List<String>           groups         = scheduler.getJobGroupNames();
        List<ScheduledJobInfo> scheduledJobs  = scheduler.getScheduledJobs();

        for( int g = 0; g < groups.size(); g++ ) {

            if( userhasDBARole || groups.get(g).equals( UserJob.JOB_GROUP ) ) {
                xmlBuf.append( "<" + SchedulerModule.PREFIX + ":group name=\"" + groups.get(g) + "\">" );

                for( int j = 0; j < scheduledJobs.size(); j++ ) {

                    if( scheduledJobs.get(j).getGroup().equals( groups.get(g) ) ) {
                        xmlBuf.append( "<" + SchedulerModule.PREFIX + ":job name=\"" + scheduledJobs.get(j).getName() + "\">" );
                        xmlBuf.append( "<" + SchedulerModule.PREFIX + ":trigger name=\"" + scheduledJobs.get(j).getTriggerName() + "\">" );
                        xmlBuf.append( "<expression>" );
                        xmlBuf.append( scheduledJobs.get(j).getTriggerExpression() );
                        xmlBuf.append( "</expression>" );
                        xmlBuf.append( "<state>" );
                        xmlBuf.append( scheduledJobs.get(j).getTriggerState() );
                        xmlBuf.append( "</state>" );
                        xmlBuf.append( "<start>" );
                        xmlBuf.append( new DateTimeValue( scheduledJobs.get(j).getStartTime() ) );
                        xmlBuf.append( "</start>" );
                        xmlBuf.append( "<end>" );

                        Date endTime = scheduledJobs.get(j).getEndTime();

                        if( endTime != null ) {
                            xmlBuf.append( new DateTimeValue( endTime ) );
                        }

                        xmlBuf.append( "</end>" );
                        xmlBuf.append( "<previous>" );

                        Date previousTime = scheduledJobs.get(j).getPreviousFireTime();

                        if( previousTime != null ) {
                            xmlBuf.append( new DateTimeValue( scheduledJobs.get(j).getPreviousFireTime() ) );
                        }

                        xmlBuf.append( "</previous>" );
                        xmlBuf.append( "<next>" );

                        Date nextTime = scheduledJobs.get(j).getNextFireTime();

                        if( nextTime != null ) {
                            xmlBuf.append( new DateTimeValue() );
                        }

                        xmlBuf.append( "</next>" );
                        xmlBuf.append( "<final>" );

                        Date finalTime = scheduledJobs.get(j).getFinalFireTime();

                        if( ( endTime != null ) && ( finalTime != null ) ) {
                            xmlBuf.append( new DateTimeValue() );
                        }

                        xmlBuf.append( "</final>" );
                        xmlBuf.append( "</" + SchedulerModule.PREFIX + ":trigger>" );
                        xmlBuf.append( "</" + SchedulerModule.PREFIX + ":job>" );
                        iJobs++;
                    }
                }

                xmlBuf.append( "</" + SchedulerModule.PREFIX + ":group>" );
            }
        }

        xmlBuf.insert( 0, "<" + SchedulerModule.PREFIX + ":jobs xmlns:scheduler=\"" + SchedulerModule.NAMESPACE_URI + "\" count=\"" + iJobs + "\">" );
        xmlBuf.append( "</" + SchedulerModule.PREFIX + ":jobs>" );

        try {
            return ModuleUtils.stringToXML( context, xmlBuf.toString());
        } catch(SAXException se) {
            throw new XPathException(this, se.getMessage(), se);
        } catch(IOException ioe) {
            throw new XPathException(this, ioe.getMessage(), ioe);
        }
    }
}
