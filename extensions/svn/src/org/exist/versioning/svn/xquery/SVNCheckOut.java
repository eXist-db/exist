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

import java.io.IOException;

import org.exist.dom.QName;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.WorkingCopy;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Checks out a working copy from a repository. Like 'svn checkout URL[@REV] PATH (-r..)' command.
 *  
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SVNCheckOut extends AbstractSVNFunction {

	public final static FunctionSignature signature[] = {
		new FunctionSignature(
			new QName("checkout", SVNModule.NAMESPACE_URI, SVNModule.PREFIX), "Checks out a working copy from a repository. Like 'svn checkout URL[@REV] PATH (-r..)' command.",
			new SequenceType[] {
				SVN_URI,
				DB_PATH
            },
            new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "value of the revision actually checked out from the repository")
		),
		new FunctionSignature(
				new QName("checkout", SVNModule.NAMESPACE_URI, SVNModule.PREFIX), "Checks out a working copy from a repository. Like 'svn checkout URL[@REV] PATH (-r..)' command.",
				new SequenceType[] {
					SVN_URI,
					DB_PATH,
					LOGIN,
					PASSWORD
	            },
	            new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "value of the revision actually checked out from the repository")
			)
	};

	public SVNCheckOut(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

		String login = "";
		String password = "";
		if (args.length == 4) {
			login = args[2].getStringValue();
			password = args[3].getStringValue();
		}
        WorkingCopy wc = new WorkingCopy(login, password);
        String uri = args[0].getStringValue();
        String destPath = args[1].getStringValue();
        
        Resource wcDir = new Resource(destPath);
        if (wcDir.exists()) {
        	IOException exception = 
        			new IOException("the destination directory '" + wcDir.getAbsolutePath() + "' already exists!");

        	LOG.debug(exception);
        	
        	throw new XPathException(this, exception);
        }
        //wcDir.mkdirs();
        
        long rev = -1;
    	try {
    		rev = wc.checkout(SVNURL.parseURIEncoded(uri), SVNRevision.HEAD, wcDir, true);
		} catch (SVNException svne) {
			svne.printStackTrace();
			
        	LOG.debug(svne);

        	throw new XPathException(this,
					"error while checking out a working copy for the location '"
                    + uri + "'", svne);
		}
		
		return new IntegerValue(rev);
	}
	
}
