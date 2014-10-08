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
import org.exist.versioning.svn.WorkingCopy;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;

/**
 * Commits files or directories into repository.
 * 
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class SVNCommit extends AbstractSVNFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("commit", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Commits files or directories into repository.",
			new SequenceType[] {
				DB_PATH,
				MESSAGE,
                LOGIN,
                PASSWORD
            },
			new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "the revision number the repository was committed to"));

    /**
     *
     * @param context
     */
    public SVNCommit(XQueryContext context) {
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

        String wcDir = args[0].getStringValue();
        String comment = args[1].getStringValue();
        String user = args[2].getStringValue();
        String password = args[3].getStringValue();
        
        SVNCommitInfo info = null;
        
        try {
        	WorkingCopy wc = new WorkingCopy(user, password);

        	info = wc.commit(new Resource(wcDir), false, comment);
		} catch (SVNException svne) {
			svne.printStackTrace();
			throw new XPathException(this,
					"error while commiting a working copy to the repository '"
                    + wcDir + "'", svne);
		}

		if (info == null)
			return new IntegerValue(-1);
		
		return new IntegerValue(info.getNewRevision());
    }
}
