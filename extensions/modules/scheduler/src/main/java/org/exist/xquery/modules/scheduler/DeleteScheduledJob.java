/*
 *  eXist Scheduler Module Extension DeleteSheduledJob
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

import org.exist.dom.QName;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.UserJob;
import org.exist.security.Subject;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * eXist Scheduler Module Extension DeleteScheduledJob.
 *
 * Removes a Job from the Scheduler
 *
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @version  1.0
 * @see      org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext, org.exist.xquery.FunctionSignature)
 * @serial   2006-11-15
 */
public class DeleteScheduledJob extends BasicFunction
{
    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "delete-scheduled-job", SchedulerModule.NAMESPACE_URI, SchedulerModule.PREFIX ),
			"Delete the named job named from the Scheduler. Will only delete User Scheduled Jobs! Returns true if the Job was deleted.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "job-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the job to be deleted" )
			},
			new FunctionReturnSequenceType( Type.BOOLEAN, Cardinality.EXACTLY_ONE, "a boolean value indicating success or failure on deleting the named job." )
		);
	
    private Scheduler                     scheduler = null;

    /**
     * DeleteScheduledJob Constructor.
     *
     * @param  context    The Context of the calling XQuery
     * @param  signature  DOCUMENT ME!
     */
    public DeleteScheduledJob( XQueryContext context, FunctionSignature signature )
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
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        String jobName = args[0].getStringValue();

        Subject   user    = context.getSubject();

        //Check if the user is a DBA
        if( !user.hasDbaRole() ) {
            return( BooleanValue.FALSE );
        }

        return( BooleanValue.valueOf( scheduler.deleteJob( jobName, UserJob.JOB_GROUP ) ) );
    }
}
