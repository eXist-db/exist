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
 * $Id$
 */
package org.exist.versioning.svn.xquery;

import org.exist.dom.QName;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.DefaultSVNOptions;
import org.exist.versioning.svn.wc.SVNClientManager;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNException;

/**
 * Recursively cleans up the working copy, removing locks and resuming unfinished operations.
 * 
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class SVNCleanup extends AbstractSVNFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("clean-up", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Recursively cleans up the working copy, removing locks and resuming unfinished operations.",
			new SequenceType[] {
				DB_PATH
            },
			new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, ""));

    /**
     *
     * @param context
     */
    public SVNCleanup(XQueryContext context) {
        super(context, signature);
    }
    /**
     * Process the function. All arguments are passed in the array args. The number of
     * arguments, their type and cardinality have already been checked to match
     * the function signature.
     *
     * @param args
     * @param contextSequence
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String uri = args[0].getStringValue();
        DefaultSVNOptions options = SVNWCUtil.createDefaultOptions(true);
        SVNClientManager manager = SVNClientManager.newInstance(options, "", "");
        
        try {
			manager.getWCClient().doCleanup(new Resource(uri));
		} catch (SVNException e) {
			throw new XPathException(this, e.getMessage(), e);
		}
        
        return Sequence.EMPTY_SEQUENCE;  
    }
}
