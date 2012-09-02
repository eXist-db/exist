/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  $Id$
 */
package org.exist.scheduler;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
class XQueryJob implements org.quartz.Job {

    @Override
    public final void execute(JobExecutionContext jec) throws JobExecutionException {
        
        final JobDataMap jobDataMap = jec.getJobDetail().getJobDataMap();
        
        final Job description = (Job)jobDataMap.get("Job");
        
        if (description.getDatabase() == null)
        	throw new JobExecutionException("Internal error, database missing, abort.");

    	if (description.scriptURI == null)
        	throw new JobExecutionException("Internal error, script URI missing, abort.");

        if (description.getSubject() == null)
        	throw new JobExecutionException("Internal error, subject missing, abort.");

        DBBroker broker = null;
        DocumentImpl resource = null;
        Source source = null;
        XQueryPool pool  = null;
        CompiledXQuery compiled = null;
        XQueryContext context = null;

        try {

            broker = description.getDatabase().get(description.getSubject());

            //get the xquery
            if(description.scriptURI.indexOf(':') > 0) {
                source = SourceFactory.getSource(broker, "", description.scriptURI, true);
            } else {
                XmldbURI pathUri = XmldbURI.create(description.scriptURI);
                resource = broker.getXMLResource(pathUri, Lock.READ_LOCK);

                if(resource != null) {
                    source = new DBSource(broker, (BinaryDocument)resource, true);
                }
            }

            if (source == null)
            	throw new JobExecutionException("Script '"+description.scriptURI+" can be read, abort.");
            
            final XQuery xquery = broker.getXQueryService();
            pool = xquery.getXQueryPool();

            compiled = pool.borrowCompiledXQuery(broker, source);

            if(compiled == null) {
                context = xquery.newContext(AccessContext.REST);
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

            if(compiled == null)
                compiled = xquery.compile(context, source);

            xquery.execute(compiled, null);

        } catch(Exception e) {
        	throw new JobExecutionException(e);

        } finally {
            if(resource != null)
                resource.getUpdateLock().release(Lock.READ_LOCK);

            if(context != null)
                context.cleanupBinaryValueInstances();
            
            if(pool != null && source != null && compiled != null)
                pool.returnCompiledXQuery(source, compiled);

            description.getDatabase().release(broker);
        }
    }
}