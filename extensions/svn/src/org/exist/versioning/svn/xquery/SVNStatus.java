/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.versioning.svn.xquery;

import org.exist.dom.QName;
import org.exist.versioning.svn.Resource;
import org.exist.versioning.svn.wc.ISVNStatusHandler;
import org.exist.versioning.svn.wc.SVNClientManager;
import org.exist.versioning.svn.wc.SVNStatusClient;
import org.exist.versioning.svn.wc.SVNWCClient;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Status information on Working Copy items.
 *
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SVNStatus extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("status", SVNModule.NAMESPACE_URI, SVNModule.PREFIX), "Status information on Working Copy items.",
			new SequenceType[] {
                new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "working copy path")
            },
            new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, ""));

	public SVNStatus(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		String uri = args[0].getStringValue();
		SVNRepositoryFactoryImpl.setup();
		SVNClientManager manager = SVNClientManager.newInstance(SVNWCUtil.createDefaultOptions(false), "", "");
		SVNStatusClient statusClient = manager.getStatusClient();
		SVNWCClient wcClient = manager.getWCClient();
		try {
			statusClient.doStatus(new Resource(uri), SVNRevision.HEAD, SVNDepth.getInfinityOrFilesDepth(true), true, true, false, false,  new AddStatusHandler(wcClient, statusClient), null);
		} catch (SVNException e1) {
			e1.printStackTrace();
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
	
    private static class AddStatusHandler implements ISVNStatusHandler {
        private final SVNWCClient wcClient;
        private final SVNStatusClient statusClient;

        public AddStatusHandler(SVNWCClient wcClient, SVNStatusClient statusClient) {
        	this.wcClient = wcClient;
        	this.statusClient = statusClient;
        }

		@Override
        public void handleStatus(org.exist.versioning.svn.wc.SVNStatus status) throws SVNException {
//            if(!SVNStatusType.STATUS_MISSING.equals(status.getContentsStatus())) {
//                if(status.getFile().isDirectory()) {
//                	System.out.println("Directory -> "+status.getFile());
//        			statusClient.doStatus(new Resource(status.getFile().toString()), SVNRevision.HEAD, SVNDepth.INFINITY, true, true, false, false,  new AddStatusHandler(wcClient, statusClient), null);
//                } else {
                	System.out.println(status.getFile()+" = "+status.getContentsStatus()+" / "+status.getRemoteContentsStatus());
//                }
                
//			wcClient.doAdd(status.getFile(), true, false, false, SVNDepth.INFINITY, false, false);
//                else
//                	wcClient.doAdd(status.getFile(), true, false, false, SVNDepth.EMPTY, false, true);
//            }
        }
    }
}
