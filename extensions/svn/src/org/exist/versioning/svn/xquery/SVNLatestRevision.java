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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class SVNLatestRevision extends AbstractSVNFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-latest-revision-number", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Returns the number of the latest revision of the subversion repository.",
			new SequenceType[] {
				SVN_URI,
				LOGIN,
				PASSWORD
            },
			new FunctionParameterSequenceType("revision-number", Type.LONG, Cardinality.EXACTLY_ONE, "The latest revision number of the repository"));


    public SVNLatestRevision(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        String uri = args[0].getStringValue();
        try {
            SVNRepository repo = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(uri));
            ISVNAuthenticationManager authManager =
                    SVNWCUtil.createDefaultAuthenticationManager(args[1].getStringValue(), args[2].getStringValue());
            repo.setAuthenticationManager(authManager);

            long latestRevision = repo.getLatestRevision();
		    return new IntegerValue(latestRevision);
        } catch (SVNException e) {
            throw new XPathException(this, e.getMessage(), e);
        }
    }
}
