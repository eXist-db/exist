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
 * $Id: SVNCommit.java 14474 2011-05-18 05:15:40Z shabanovd $
 */
package org.exist.versioning.svn.xquery;

import java.io.File;

import org.exist.dom.QName;
import org.exist.util.io.Resource;
import org.exist.versioning.svn.WorkingCopy;
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
 * Restores the pristine version of working copy path, 
 * effectively undoing any local mods.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class SVNRevert extends AbstractSVNFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("revert", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Restores the pristine version of working copy path, effectively undoing any local mods.",
			new SequenceType[] {
				DB_PATH
            },
			new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, ""));

    /**
     *
     * @param context
     */
    public SVNRevert(XQueryContext context) {
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
        
        try {
        	WorkingCopy wc = new WorkingCopy("", "");

        	wc.revert(new File[] { new Resource(wcDir) });

        } catch (SVNException svne) {
			svne.printStackTrace();
			throw new XPathException(this,
					"error while revert '" + wcDir + "'", svne);
		}

		return Sequence.EMPTY_SEQUENCE;
    }
}
