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

import org.apache.log4j.Logger;

import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.exist.EXistException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
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

import java.io.IOException;

import java.net.MalformedURLException;

import java.util.Enumeration;
import java.util.Properties;


/**
 * Class to represent a User's XQuery Job Extends UserJob.
 *
 * @author  Adam Retter <adam@exist-db.org>
 * @author  Andrzej Taramina <andrzej@chaeron.com>
 */
public class UserXQueryJob extends UserJob
{
    protected final static Logger LOG            = Logger.getLogger( UserXQueryJob.class );

    private String                JOB_NAME       = "XQuery";

    private String                XQueryResource = null;
    private Subject               user           = null;

    /**
     * Default Constructor for Quartz.
     */
    public UserXQueryJob()
    {
    }


    /**
     * Constructor for Creating a new XQuery User Job.
     *
     * @param  jobName         DOCUMENT ME!
     * @param  XQueryResource  DOCUMENT ME!
     * @param  user            DOCUMENT ME!
     */
    public UserXQueryJob( String jobName, String XQueryResource, Subject user )
    {
        this.XQueryResource = XQueryResource;
        this.user           = user;

        if( jobName == null ) {
            this.JOB_NAME += ": " + XQueryResource;
        } else {
            this.JOB_NAME = jobName;
        }
    }

    public final String getName()
    {
        return( JOB_NAME );
    }


    public void setName( String name )
    {
        JOB_NAME = name;
    }


    /**
     * Returns the XQuery Resource for this Job.
     *
     * @return  The XQuery Resource for this Job
     */
    protected String getXQueryResource()
    {
        return( XQueryResource );
    }


    /**
     * Returns the User for this Job.
     *
     * @return  The User for this Job
     */
    protected Subject getUser()
    {
        return( user );
    }


    public final void execute( JobExecutionContext jec ) throws JobExecutionException
    {
        JobDataMap jobDataMap     = jec.getJobDetail().getJobDataMap();
        BrokerPool pool           = (BrokerPool)jobDataMap.get( "brokerpool" );
        DBBroker   broker         = null;
        String     xqueryresource = (String)jobDataMap.get( "xqueryresource" );
        Subject    user           = (Subject)jobDataMap.get( "user" );
        Properties params         = (Properties)jobDataMap.get( "params" );
		boolean	   unschedule	  = ((Boolean)jobDataMap.get( "unschedule" )).booleanValue();

        //if invalid arguments then abort
        if( ( pool == null ) || ( xqueryresource == null ) || ( user == null ) ) {
            abort( "BrokerPool or XQueryResource or User was null!" );
        }

        DocumentImpl   resource = null;
        Source         source   = null;
        XQueryPool     xqPool   = null;
        CompiledXQuery compiled = null;

        try {

            //get the xquery
            broker = pool.get( user );

            if( xqueryresource.indexOf( ':' ) > 0 ) {
                source = SourceFactory.getSource( broker, "", xqueryresource, true );
            } else {
                XmldbURI pathUri = XmldbURI.create( xqueryresource );
                resource = broker.getXMLResource( pathUri, Lock.READ_LOCK );

                if( resource != null ) {
                    source = new DBSource( broker, ( BinaryDocument )resource, true );
                }
            }

            if( source != null ) {

                //execute the xquery
                XQuery xquery = broker.getXQueryService();
                xqPool = xquery.getXQueryPool();
                XQueryContext context;

                //try and get a pre-compiled query from the pool
                compiled = xqPool.borrowCompiledXQuery( broker, source );

                if( compiled == null ) {
                    context = xquery.newContext( AccessContext.REST );
                } else {
                    context = compiled.getContext();
                }

                //TODO: don't hardcode this?
                if( resource != null ) {
                    context.setModuleLoadPath( XmldbURI.EMBEDDED_SERVER_URI.append( resource.getCollection().getURI() ).toString() );
                    context.setStaticallyKnownDocuments( new XmldbURI[] { resource.getCollection().getURI() } );
                }

                if( compiled == null ) {

                    try {
                        compiled = xquery.compile( context, source );
                    }
                    catch( IOException e ) {
                        abort( "Failed to read query from " + xqueryresource );
                    }
                }

                //declare any parameters as external variables
                if( params != null ) {
                    String bindingPrefix = params.getProperty( "bindingPrefix" );

                    if( bindingPrefix == null ) {
                        bindingPrefix = "local";
                    }
                    Enumeration paramNames = params.keys();

                    while( paramNames.hasMoreElements() ) {
                        String name  = ( String )paramNames.nextElement();
                        String value = params.getProperty( name );
                        context.declareVariable( bindingPrefix + ":" + name, new StringValue( value ) );
                    }
                }

                xquery.execute( compiled, null );

            } else {
                LOG.warn( "XQuery User Job not found: " + xqueryresource + ", job not scheduled" );
            }
        }
        catch( EXistException ee ) {
            abort( "Could not get DBBroker!" );
        }
        catch( PermissionDeniedException pde ) {
            abort( "Permission denied for the scheduling user: " + user.getName() + "!" );
        }
        catch( XPathException xpe ) {
            abort( "XPathException in the Job: " + xpe.getMessage() + "!", unschedule );
        }
        catch( MalformedURLException e ) {
            abort( "Could not load XQuery: " + e.getMessage() );
        }
        catch( IOException e ) {
            abort( "Could not load XQuery: " + e.getMessage() );
        }
        finally {

            //return the compiled query to the pool
            if( ( xqPool != null ) && ( source != null ) && ( compiled != null ) ) {
                xqPool.returnCompiledXQuery( source, compiled );
            }

            //release the lock on the xquery resource
            if( resource != null ) {
                resource.getUpdateLock().release( Lock.READ_LOCK );
            }

            // Release the DBBroker
            if( ( pool != null ) && ( broker != null ) ) {
                pool.release( broker );
            }
        }

    }

	private void abort( String message ) throws JobExecutionException
    {
		abort( message, true );
	}
	

    private void abort( String message, boolean unschedule ) throws JobExecutionException
    {
        JobExecutionException jaa = new JobExecutionException( "UserXQueryJob Failed: " + message + ( unschedule ? " Unscheduling UserXQueryJob." : "" ), false );
		
		//abort all triggers for this job if specified that we should unschedule the job
        jaa.setUnscheduleAllTriggers( unschedule );

        throw( jaa );
    }
}
