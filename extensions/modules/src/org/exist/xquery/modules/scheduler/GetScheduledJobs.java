/*
 *  eXist Scheduler Module Extension GetSheduledJobs
 *  Copyright (C) 2006 Adam Retter <adam.retter@devon.gov.uk>
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

import java.util.Date;

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.scheduler.ScheduledJobInfo;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.UserJob;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.xml.sax.SAXException;

/**
 * eXist Scheduler Module Extension GetScheduledJobs
 * 
 * Retrieves details of Jobs that have been Scheduled
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2007-12-04
 * @version 1.1
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class GetScheduledJobs extends BasicFunction
{	
	private Scheduler 	scheduler 	= null;
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "get-scheduled-jobs", SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX),
			"Get's details of all Scheduled Jobs",
			null,
			new SequenceType( Type.NODE, Cardinality.EXACTLY_ONE ) );
	
	/**
	 * GetScheduledJobs Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public GetScheduledJobs( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
		
		scheduler 	= context.getBroker().getBrokerPool().getScheduler();
    }

	/**
	 * evaluate the call to the xquery function,
	 * it is really the main entry point of this class
	 * 
	 * @param args		arguments from the  function call
	 * @param contextSequence	the Context Sequence to operate on (not used here internally!)
	 * @return		A sequence representing the result of the function call
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		User user = context.getUser();
		
		boolean userhasDBARole = user.hasDbaRole();
		
		StringBuffer xmlBuf = new StringBuffer();
		
		int iJobs = 0;
		String[] groups = scheduler.getJobGroupNames();
		ScheduledJobInfo[] scheduledJobs = scheduler.getScheduledJobs();
		
		for(int g = 0; g < groups.length; g++)
		{
			if(userhasDBARole || groups[g].equals(UserJob.JOB_GROUP))
			{
				xmlBuf.append("<" + SchedulerModule.PREFIX + ":group name=\"" + groups[g] + "\">");
				for(int j = 0; j < scheduledJobs.length; j++)
				{
					if(scheduledJobs[j].getGroup().equals(groups[g]))
					{
						xmlBuf.append("<" + SchedulerModule.PREFIX + ":job name=\"" + scheduledJobs[j].getName() + "\">");
						xmlBuf.append("<" + SchedulerModule.PREFIX + ":trigger name=\"" + scheduledJobs[j].getTriggerName() + "\">");
						xmlBuf.append("<expression>");
						xmlBuf.append(scheduledJobs[j].getTriggerExpression());
						xmlBuf.append("</expression>");
						xmlBuf.append("<state>");
						xmlBuf.append(scheduledJobs[j].getTriggerState());
						xmlBuf.append("</state>");
						xmlBuf.append("<start>");
						xmlBuf.append(new DateTimeValue(scheduledJobs[j].getStartTime()));
						xmlBuf.append("</start>");
						xmlBuf.append("<end>");
						
						Date endTime = scheduledJobs[j].getEndTime();
						if(endTime != null)
						{
							xmlBuf.append(new DateTimeValue(endTime));
						}
					
						xmlBuf.append("</end>");
						xmlBuf.append("<previous>");
						
						Date previousTime = scheduledJobs[j].getPreviousFireTime();
						if(previousTime != null)
						{
							xmlBuf.append(new DateTimeValue(scheduledJobs[j].getPreviousFireTime()));
						}
				
						xmlBuf.append("</previous>");
						xmlBuf.append("<next>");
						
						Date nextTime = scheduledJobs[j].getNextFireTime();
						if(nextTime != null)
						{
							xmlBuf.append(new DateTimeValue());
						}

						xmlBuf.append("</next>");
						xmlBuf.append("<final>");
						
						Date finalTime = scheduledJobs[j].getFinalFireTime();
						if(endTime != null && finalTime != null)
						{
							xmlBuf.append(new DateTimeValue());
						}
		
						xmlBuf.append("</final>");
						xmlBuf.append("</" + SchedulerModule.PREFIX + ":trigger>");
						xmlBuf.append("</" + SchedulerModule.PREFIX + ":job>");
						iJobs++;
					}
				}
				
				xmlBuf.append("</" + SchedulerModule.PREFIX + ":group>");
			}
		}
		
		xmlBuf.insert(0, "<" + SchedulerModule.PREFIX + ":jobs xmlns:scheduler=\"" + SchedulerModule.NAMESPACE_URI + "\" count=\"" + iJobs + "\">");
		xmlBuf.append("</" + SchedulerModule.PREFIX + ":jobs>");
		
		try
		{
			return(ModuleUtils.stringToXML(context, xmlBuf.toString()));
		}
		catch(SAXException se)
		{
			throw(new XPathException(se));
		}
	}
}