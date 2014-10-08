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
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * Updates a working copy (brings changes from the repository into the working copy). Like 'svn update PATH' command.
 *  
 * @author <a href="mailto:amir.akhmedov@gmail.com">Amir Akhmedov</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SVNUpdate extends AbstractSVNFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("update", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Updates a working copy (brings changes from the repository into the working copy). Like 'svn update PATH' command.",
			new SequenceType[] {
				DB_PATH,
                LOGIN,
                PASSWORD
            },
			new FunctionReturnSequenceType(Type.LONG, Cardinality.EXACTLY_ONE, "revision to which revision was resolved"));

    /**
     *
     * @param context
     */
    public SVNUpdate(XQueryContext context) {
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
        String user = args[1].getStringValue();
        String password = args[2].getStringValue();
    	
    	WorkingCopy wc = new WorkingCopy(user, password);
    	
    	long update = -1;
    	
    	try {
    		update = wc.update(new Resource(uri), SVNRevision.HEAD, true);
    	} catch (SVNException e) {
            throw new XPathException(this, e.getMessage(), e);
		}
		
    	return new IntegerValue(update);
    }
}
