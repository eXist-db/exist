/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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

import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public abstract class AbstractSVNFunction extends BasicFunction {

	public AbstractSVNFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

    //common parameters description
    protected static FunctionParameterSequenceType LOGIN = new FunctionParameterSequenceType("login", Type.STRING, Cardinality.EXACTLY_ONE, "Login to authenticate on svn server.");
    protected static FunctionParameterSequenceType PASSWORD = new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "Passord to authenticate on svn server.");

    protected static FunctionParameterSequenceType DB_PATH = new FunctionParameterSequenceType("database-path", Type.STRING, Cardinality.EXACTLY_ONE, "A database URI.");

    protected static final FunctionParameterSequenceType SVN_URI = new FunctionParameterSequenceType("repository-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The subversion repository URI.");
    
    protected static final FunctionParameterSequenceType MESSAGE = new FunctionParameterSequenceType("message", Type.STRING, Cardinality.ZERO_OR_ONE, "The message.");
    //protected static final FunctionParameterSequenceType MESSAGE = new FunctionParameterSequenceType("message", Type.STRING, Cardinality.ZERO_OR_ONE, "The SVN commit message.");

}
