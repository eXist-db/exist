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
package org.exist.scheduler;

import java.io.IOException;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
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
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 */
public class UserXQueryJob extends UserJob {
    
    protected final static Logger LOG = LogManager.getLogger(UserXQueryJob.class);

    private final String DEFAULT_JOB_NAME_PREFIX = "XQuery";

    private String name;
    private final String xqueryResource;
    private final Subject subject;

    /**
     * Default Constructor for Quartz.
     */
    public UserXQueryJob(){
        xqueryResource = null;
        subject = null;
    }

    /**
     * Constructor for Creating a new XQuery User Job.
     *
     * @param  jobName         The name of the job
     * @param  xqueryResource  The XQuery itself
     * @param  subject         The subject under which the xquery should be executed
     */
    public UserXQueryJob(final String jobName, final String xqueryResource, final Subject subject) {
        this.xqueryResource = xqueryResource;
        this.subject = subject;

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
     * @deprecated use getCurrentSubject method
     */
    @Deprecated
    public Subject getUser() {
        return subject;
    }

    /**
     * Returns the subject for this Job.
     *
     * @return  The subject for this Job
     */
    public Subject getSubject() {
        return subject;
    }

    @Override
    public final void execute(final JobExecutionContext jec) throws JobExecutionException {
        
        final JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
        
        //TODO why are these values not used from the class members?
        final String xqueryResource = (String)jobDataMap.get(XQUERY_SOURCE);
        final Subject user = (Subject)jobDataMap.get(ACCOUNT);
        
        final BrokerPool pool = (BrokerPool)jobDataMap.get(DATABASE);
        final Properties params = (Properties)jobDataMap.get(PARAMS);
        final boolean unschedule = ((Boolean)jobDataMap.get(UNSCHEDULE));

        //if invalid arguments then abort
        if((pool == null) || (xqueryResource == null) || (user == null)) {
            abort("BrokerPool or XQueryResource or User was null!");
        }

        try (final DBBroker broker = pool.get(Optional.of(user))) {
            if(xqueryResource.indexOf(':') > 0) {
                final Source source = SourceFactory.getSource(broker, "", xqueryResource, true);
                if(source != null) {
                    executeXQuery(pool,  broker, source, params);
                    return;
                }
            } else {
                final XmldbURI pathUri = XmldbURI.create(xqueryResource);
                try(final LockedDocument lockedResource = broker.getXMLResource(pathUri, LockMode.READ_LOCK)) {
                    if (lockedResource != null) {
                        final Source source = new DBSource(pool, (BinaryDocument) lockedResource.getDocument(), true);
                        executeXQuery(pool, broker, source, params);
                        return;
                    }
                }
            }

            LOG.warn("XQuery User Job not found: {}, job not scheduled", xqueryResource);
        } catch(final EXistException ee) {
            abort("Could not get DBBroker!");
        } catch(final PermissionDeniedException pde) {
            abort("Permission denied for the scheduling user: " + user.getName() + "!");
        } catch(final XPathException xpe) {
            abort("XPathException in the Job: " + xpe.getMessage() + "!", unschedule);
        } catch(final IOException e) {
            abort("Could not load XQuery: " + e.getMessage());
        }
    }

    private void executeXQuery(final BrokerPool pool, final DBBroker broker, final Source source, final Properties params) throws PermissionDeniedException, XPathException, JobExecutionException {
        XQueryPool xqPool  = null;
        CompiledXQuery compiled = null;
        XQueryContext context = null;

        try {
            //execute the xquery
            final XQuery xquery = pool.getXQueryService();
            xqPool = pool.getXQueryPool();

            //try and get a pre-compiled query from the pool
            compiled = xqPool.borrowCompiledXQuery(broker, source);

            if (compiled == null) {
                context = new XQueryContext(pool);
            } else {
                context = compiled.getContext();
                context.prepareForReuse();
            }

            if(source instanceof  DBSource) {
                final XmldbURI collectionUri = ((DBSource)source).getDocumentPath().removeLastSegment();
                context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.append(collectionUri.getCollectionPath()).toString());
                context.setStaticallyKnownDocuments(new XmldbURI[] { collectionUri });
            }

            if (compiled == null) {

                try {
                    compiled = xquery.compile(context, source);
                } catch (final IOException e) {
                    abort("Failed to read query from " + xqueryResource);
                }
            }

            //declare any parameters as external variables
            if (params != null) {
                String bindingPrefix = params.getProperty("bindingPrefix");

                if (bindingPrefix == null) {
                    bindingPrefix = "local";
                }


                for (final Entry param : params.entrySet()) {
                    final String key = (String) param.getKey();
                    final String value = (String) param.getValue();
                    context.declareVariable(bindingPrefix + ":" + key, new StringValue(value));
                }
            }

            xquery.execute(broker, compiled, null);
        } finally {
            if(context != null) {
                context.runCleanupTasks();
            }

            //return the compiled query to the pool
            if(xqPool != null && source != null && compiled != null) {
                xqPool.returnCompiledXQuery(source, compiled);
            }
        }
    }


    private void abort(final String message) throws JobExecutionException {
        abort(message, true);
    }

    private void abort(final String message, final boolean unschedule) throws JobExecutionException {
        final JobExecutionException jaa = new JobExecutionException(
            "UserXQueryJob Failed: " + message + (unschedule ? " Unscheduling UserXQueryJob." : ""),
            false
        );

        //abort all triggers for this job if specified that we should unschedule the job
        jaa.setUnscheduleAllTriggers(unschedule);

        throw jaa;
    }
}