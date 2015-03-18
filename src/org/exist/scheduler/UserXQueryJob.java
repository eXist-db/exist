/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db team
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
import java.net.MalformedURLException;
import java.util.Map.Entry;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.StringValue;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;


/**
 * Class to represent a User's XQuery Job Extends UserJob.
 *
 * @author  Adam Retter <adam@exist-db.org>
 * @author  Andrzej Taramina <andrzej@chaeron.com>
 */
public class UserXQueryJob extends UserJob {
    
    protected final static Logger LOG = LogManager.getLogger(UserXQueryJob.class);

    private final String DEFAULT_JOB_NAME_PREFIX = "XQuery";

    private String name;
    private final String xqueryResource;
    private final Subject user;

    /**
     * Default Constructor for Quartz.
     */
    public UserXQueryJob(){
        xqueryResource = null;
        user = null;
    }


    /**
     * Constructor for Creating a new XQuery User Job.
     *
     * @param  jobName         The name of the job
     * @param  xqueryResource  The XQuery itself
     * @param  user            The user under which the xquery should be executed
     */
    public UserXQueryJob(final String jobName, final String xqueryResource, final Subject user) {
        this.xqueryResource = xqueryResource;
        this.user = user;

        if(jobName == null) {
            this.name = DEFAULT_JOB_NAME_PREFIX + ": " + xqueryResource;
        } else {
            this.name = jobName;
        }
    }

    @Override
    public final String getName() {
        return name ;
    }

    @Override
    public void setName(final String name) {
        this.name = name;
    }

    /**
     * Returns the XQuery Resource for this Job.
     *
     * @return  The XQuery Resource for this Job
     */
    public String getXQueryResource() {
        return xqueryResource;
    }

    /**
     * Returns the User for this Job.
     *
     * @return  The User for this Job
     */
    public Subject getUser() {
        return user;
    }

    @Override
    public final void execute(final JobExecutionContext jec) throws JobExecutionException {
        
        final JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
        
        //TODO why are these values not used from the class members?
        final String xqueryresource = (String)jobDataMap.get("xqueryresource");
        final Subject user = (Subject)jobDataMap.get("user");
        
        final BrokerPool pool = (BrokerPool)jobDataMap.get("brokerpool");
        final Properties params = (Properties)jobDataMap.get("params");
        final boolean unschedule = ((Boolean)jobDataMap.get("unschedule")).booleanValue();

        //if invalid arguments then abort
        if((pool == null) || (xqueryresource == null) || (user == null)) {
            abort("BrokerPool or XQueryResource or User was null!");
        }

        DBBroker broker = null;
        DocumentImpl resource = null;
        Source source = null;
        XQueryPool xqPool  = null;
        CompiledXQuery compiled = null;
        XQueryContext context = null;

        try {

            //get the xquery
            broker = pool.get(user);

            if(xqueryresource.indexOf(':') > 0) {
                source = SourceFactory.getSource(broker, "", xqueryresource, true);
            } else {
                final XmldbURI pathUri = XmldbURI.create(xqueryresource);
                resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

                if(resource != null) {
                    source = new DBSource(broker, (BinaryDocument)resource, true);
                }
            }

            if(source != null) {

                //execute the xquery
                final XQuery xquery = broker.getXQueryService();
                xqPool = xquery.getXQueryPool();

                //try and get a pre-compiled query from the pool
                compiled = xqPool.borrowCompiledXQuery(broker, source);

                if(compiled == null) {
                    context = xquery.newContext(AccessContext.REST); //TODO should probably have its own AccessContext.SCHEDULER
                } else {
                    context = compiled.getContext();
                }

                //TODO: don't hardcode this?
                if(resource != null) {
                    context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(resource.getCollection().getURI()).toString());
                    context.setStaticallyKnownDocuments(new XmldbURI[] {
                        resource.getCollection().getURI()
                    });
                }

                if(compiled == null) {

                    try {
                        compiled = xquery.compile(context, source);
                    }
                    catch(final IOException e) {
                        abort("Failed to read query from " + xqueryresource);
                    }
                }

                //declare any parameters as external variables
                if(params != null) {
                    String bindingPrefix = params.getProperty("bindingPrefix");

                    if(bindingPrefix == null) {
                        bindingPrefix = "local";
                    }
                    

                    for(final Entry param : params.entrySet()) {
                        final String key = (String)param.getKey();
                        final String value = (String)param.getValue();
                        context.declareVariable( bindingPrefix + ":" + key, new StringValue(value));
                    }
                }

                xquery.execute(compiled, null);

            } else {
                LOG.warn("XQuery User Job not found: " + xqueryresource + ", job not scheduled");
            }
        } catch(final EXistException ee) {
            abort("Could not get DBBroker!");
        } catch(final PermissionDeniedException pde) {
            abort("Permission denied for the scheduling user: " + user.getName() + "!");
        } catch(final XPathException xpe) {
            abort("XPathException in the Job: " + xpe.getMessage() + "!", unschedule);
        } catch(final MalformedURLException e) {
            abort("Could not load XQuery: " + e.getMessage());
        } catch(final IOException e) {
            abort("Could not load XQuery: " + e.getMessage());
        } finally {

            if(context != null) {
                context.runCleanupTasks();
            }
            
            //return the compiled query to the pool
            if(xqPool != null && source != null && compiled != null) {
                xqPool.returnCompiledXQuery(source, compiled);
            }

            //release the lock on the xquery resource
            if(resource != null) {
                resource.getUpdateLock().release(Lock.READ_LOCK);
            }

            // Release the DBBroker
            if(pool != null && broker != null) {
                pool.release(broker);
            }
        }

    }

    private void abort(final String message) throws JobExecutionException {
        abort(message, true);
    }
	

    private void abort(final String message, final boolean unschedule) throws JobExecutionException {
        final JobExecutionException jaa = new JobExecutionException("UserXQueryJob Failed: " + message + (unschedule ? " Unscheduling UserXQueryJob." : ""), false);
		
        //abort all triggers for this job if specified that we should unschedule the job
        jaa.setUnscheduleAllTriggers(unschedule);

        throw jaa;
    }
}