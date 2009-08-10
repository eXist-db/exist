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

import java.util.Date;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.exist.storage.ProcessMonitor;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class GetRunningJobs extends BasicFunction {

    protected final static Logger logger = Logger.getLogger(GetRunningJobs.class);

    final static String NAMESPACE_URI                       = SystemModule.NAMESPACE_URI;
    final static String PREFIX                              = SystemModule.PREFIX;

    public final static FunctionSignature signature =
        new FunctionSignature(
                new QName( "get-running-jobs", SystemModule.NAMESPACE_URI, SystemModule.PREFIX ),
                "Get a list of running jobs (dba role only).",
                null,
                new FunctionReturnSequenceType( Type.ITEM, Cardinality.EXACTLY_ONE, "the list of running jobs" )
        );

    public GetRunningJobs(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if( !context.getUser().hasDbaRole() ) {
            throw( new XPathException( this, "Permission denied, calling user '" + context.getUser().getName() + "' must be a DBA to get the list of running xqueries" ) );
        }

        MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement( new QName( "jobs", NAMESPACE_URI, PREFIX ), null );

        BrokerPool brokerPool = context.getBroker().getBrokerPool();
		ProcessMonitor monitor = brokerPool.getProcessMonitor();
        ProcessMonitor.JobInfo[] jobs = monitor.runningJobs();
        for (int i = 0; i < jobs.length; i++) {
            Thread process = jobs[i].getThread();
			Date startDate = new Date(jobs[i].getStartTime());
            builder.startElement( new QName( "job", NAMESPACE_URI, PREFIX ), null);
            builder.addAttribute( new QName("id", null, null), process.getName());
            builder.addAttribute( new QName("action", null, null), jobs[i].getAction());
			builder.addAttribute( new QName("start", null, null), new DateTimeValue(startDate).getStringValue());
            builder.addAttribute(new QName("info", null, null), jobs[i].getAddInfo().toString());
            builder.endElement();
        }

        builder.endElement();
        builder.endDocument();

        return (NodeValue)builder.getDocument().getDocumentElement();
    }
}
