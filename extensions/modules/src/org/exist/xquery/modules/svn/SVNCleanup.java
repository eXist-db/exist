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
package org.exist.xquery.modules.svn;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
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

/**
 * Created by IntelliJ IDEA.
 * User: lcahlander
 * Date: Apr 22, 2010
 * Time: 9:48:14 AM
 * To change this template use File | Settings | File Templates.
 */
public class SVNCleanup extends BasicFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("clean-up", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"? a resource to a subversion repository.\n\nThis is a stub and currently does nothing.",
			new SequenceType[] {
                new FunctionParameterSequenceType("repository-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The location in the subversion repository URI"),
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The subversion username"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The subversion password"),
                new FunctionParameterSequenceType("resource", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource."),
                new FunctionParameterSequenceType("delete", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "")
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
//        DAVRepositoryFactory.setup();
//        SVNRepositoryFactoryImpl.setup();
//        String uri = args[0].getStringValue();
//        try {
//            SVNRepository repo =
//                    SVNRepositoryFactory.create(SVNURL.parseURIDecoded(uri));
//            ISVNAuthenticationManager authManager =
//                    SVNWCUtil.createDefaultAuthenticationManager(args[1].getStringValue(), args[2].getStringValue());
//            repo.setAuthenticationManager(authManager);
//
//        } catch (SVNException e) {
//            throw new XPathException(this, e.getMessage(), e);
//        }
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
