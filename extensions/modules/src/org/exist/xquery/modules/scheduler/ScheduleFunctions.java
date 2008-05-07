/*
 *  eXist Scheduler Module Extension ScheduleFunctions
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

import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.UserJavaJob;
import org.exist.scheduler.UserJob;
import org.exist.scheduler.UserXQueryJob;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist Scheduler Module Extension ScheduleFunctions
 * 
 * Schedules job's with eXist's Scheduler  
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @serial 2006-11-15
 * @version 1.0
 *
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 */
public class ScheduleFunctions extends BasicFunction
{	
	private Scheduler scheduler = null;
	
	public final static FunctionSignature [] signatures = {
		new FunctionSignature(
			new QName("schedule-java-cron-job", SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX),
			"Schedules the Java Class named in $a (the class must extend org.exist.scheduler.UserJob) according to the Cron expression in $b",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)),
			
		new FunctionSignature(
			new QName("schedule-xquery-cron-job", SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX),
			"Schedules the XQuery resource named in $a (e.g. /db/foo.xql) according to the Cron expression in $b",
			new SequenceType[]
			{
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE))
	};
	
	/**
	 * ScheduleFunctions Constructor
	 * 
	 * @param context	The Context of the calling XQuery
	 */
	public ScheduleFunctions(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
		
		scheduler = context.getBroker().getBrokerPool().getScheduler();
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
		String resource = args[0].getStringValue();
		String cronExpression = args[1].getStringValue();
		
		User user = context.getUser();
		
		//Check if the user is a DBA
		if(!user.hasDbaRole())
		{
			return(BooleanValue.FALSE);
		}
		
		Object job = null;
		
		//scheule-xquery-cron-job
		if(isCalledAs("schedule-xquery-cron-job"))
		{
			job = new UserXQueryJob(null, resource, user);
		}
		
		//schedule-java-cron-job
		else if(isCalledAs("schedule-java-cron-job"))
		{
			try
			{
				//Check if the Class is a UserJob
				Class jobClass = Class.forName(resource);
				job = jobClass.newInstance();
				if(!(job instanceof UserJavaJob))
				{
					LOG.error("Cannot Schedule job. Class " + resource + " is not an instance of org.exist.scheduler.UserJavaJob");
					return(BooleanValue.FALSE);
				}
			}
			catch(ClassNotFoundException cnfe)
			{
				LOG.error(cnfe);
				return(BooleanValue.FALSE);
			}
			catch(IllegalAccessException iae)
			{
				LOG.error(iae);
				return(BooleanValue.FALSE);
			}
			catch(InstantiationException ie)
			{
				LOG.error(ie);
				return(BooleanValue.FALSE);
			}
		}
		
		if(job != null)
		{
			//schedule the job
			if(scheduler.createCronJob(cronExpression, (UserJob)job, null))
			{
				return(BooleanValue.TRUE);
			}
			else
			{
				return(BooleanValue.FALSE);
			}
		}
		else
		{
			return(BooleanValue.FALSE);
		}
	}
}