/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2006 The eXist team
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.scheduler;

import java.io.IOException;

import org.exist.EXistException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.source.DBSource;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Class to represent a User's XQuery Job
 * Extends UserJob
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class UserXQueryJob extends UserJob
{
	private String JOB_NAME = "XQuery";
	
	private String XQueryResource = null;
	private User user = null;
	
	/**
	 * Default Constructor for Quartz
	 */
	public UserXQueryJob()
	{		
	}
	
	/**
	 * Constructor for Creating a new XQuery User Job
	 */
	public UserXQueryJob(String XQueryResource, User user)
	{
		this.XQueryResource = XQueryResource;
		this.user = user;
		
		this.JOB_NAME += ": " + XQueryResource;
	}
	
	public final String getName()
	{
		return JOB_NAME;	
	}
	
	/**
	 * Returns the XQuery Resource for this Job
	 * 
	 * @return The XQuery Resource for this Job
	 */
	protected String getXQueryResource()
	{
		return XQueryResource;
	}
	
	/**
	 * Returns the User for this Job
	 * 
	 * @return The User for this Job
	 */
	protected User getUser()
	{
		return user;
	}
	
	public final void execute(JobExecutionContext jec) throws JobExecutionException
	{
		JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
		BrokerPool pool = (BrokerPool)jobDataMap.get("brokerpool");
		String xqueryresource = (String)jobDataMap.get("xqueryresource");
		User user = (User)jobDataMap.get("user");
		
		//if invalid arguments then abort
		if(pool == null || xqueryresource == null || user == null)
		{
			abort("BrokerPool or XQueryResource or User was null!");
		}
		
		try
		{
			//get the xquery
			DBBroker broker = pool.get(user);
			XmldbURI pathUri = XmldbURI.create(xqueryresource);
			DocumentImpl resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);
			Source source = new DBSource(broker, (BinaryDocument)resource, true);
			
			//execute the xquery
			XQuery xquery = broker.getXQueryService();
	        XQueryPool xqPool = xquery.getXQueryPool();
	        XQueryContext context;
	        
	        //try and get a pre-compiled query from the pool
	        CompiledXQuery compiled = xqPool.borrowCompiledXQuery(broker, source);
	        if(compiled == null)
	        {
	            context = xquery.newContext(AccessContext.REST);
	    	}
	        else
	        {
	            context = compiled.getContext();
	        }
	        
	        //TODO: don't hardcode this?
	        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(resource.getCollection().getURI()).toString());
	        context.setStaticallyKnownDocuments(
	                 new XmldbURI[] { resource.getCollection().getURI() }
	        );
	        
	        if(compiled == null)
	        {
	            try
	            {
	                compiled = xquery.compile(context, source);
	            }
	            catch(IOException e)
	            {
	                abort("Failed to read query from " + resource.getURI());
	            }
	        }
	        
	        try
	        {
	            xquery.execute(compiled, null);
	        }
	        finally
	        {
	        	//return the compiled query to the pool
	            xqPool.returnCompiledXQuery(source, compiled);
	        }
		}
		catch(EXistException ee)
		{
			abort("Could not get DBBroker!");
		}
		catch(PermissionDeniedException pde)
		{
			abort("Permission denied for the scheduling user: " + user.getName() + "!");
		}
		catch(XPathException xpe)
		{
			abort("XPathException in the Job: " + xpe.getMessage() + "!");
		}
		
	}
	
	private void abort(String message) throws JobExecutionException
	{
		//abort all triggers for this job
		JobExecutionException jaa = new JobExecutionException("UserXQueryJob Failed: " + message + " Unscheduling UserXQueryJob.", false);
		jaa.setUnscheduleAllTriggers(true);
		throw jaa;
	}
}
