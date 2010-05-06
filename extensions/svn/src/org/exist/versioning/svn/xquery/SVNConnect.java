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
import org.exist.http.servlets.SessionWrapper;
import org.exist.versioning.svn.old.Subversion;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.*;
import org.tmatesoft.svn.core.SVNException;

/**
 * Created by IntelliJ IDEA.
 * User: lcahlander
 * Date: Apr 22, 2010
 * Time: 9:48:14 AM
 * To change this template use File | Settings | File Templates.
 */
public class SVNConnect extends BasicFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("connect", SVNModule.NAMESPACE_URI, SVNModule.PREFIX),
			"Establishes a connection to a subversion repository.\n\nThis is a stub and currently does nothing.",
			new SequenceType[] {
                new FunctionParameterSequenceType("connection-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the connection"),
                new FunctionParameterSequenceType("collection-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The eXist collection URI"),
                new FunctionParameterSequenceType("repository-url", Type.STRING, Cardinality.EXACTLY_ONE, "The subversion repository URL"),
                new FunctionParameterSequenceType("username", Type.STRING, Cardinality.ZERO_OR_ONE, "The subversion username"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The subversion password"),
            },
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true(), if successful."));

    /**
     *
     * @param context
     */
    public SVNConnect(XQueryContext context) {
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
        boolean returnValue = false;
//        DAVRepositoryFactory.setup();
//        SVNRepositoryFactoryImpl.setup();
        String connectionName = args[0].getStringValue();
        AnyURIValue collectionURI = (AnyURIValue) args[1].itemAt(0);
        String subversionPath = args[2].getStringValue();
        String svnUsername = "anonymous";
        String svnPassword = "anonymous";
        JavaObjectValue session;

        if (!args[3].isEmpty()) {
            svnUsername = args[3].getStringValue();
        }

        if (!args[4].isEmpty()) {
            svnPassword = args[4].getStringValue();
        }

        try {
        XmldbURI collection = collectionURI.toXmldbURI();
        Subversion subversion = new Subversion(collection, subversionPath, svnUsername, svnPassword);
        SessionModule myModule = (SessionModule)context.getModule( SessionModule.NAMESPACE_URI );

        // session object is read from global variable $session
        Variable var = myModule.resolveVariable( SessionModule.SESSION_VAR );

        if( var == null || var.getValue() == null ) {
            // No saved session, so create one
            throw( new XPathException( this, "Type error: variable $session is not bound to a session object" ) );
//            session = SessionModule.createSession( context, this );
        } else if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
            throw( new XPathException( this, "Variable $session is not bound to a Java object." ) );
        } else {
            session = (JavaObjectValue)var.getValue().itemAt( 0 );
        }

        if( session.getObject() instanceof SessionWrapper) {
            ((SessionWrapper)session.getObject()).setAttribute (connectionName, subversion );
        } else {
            throw( new XPathException( this, "Type error: variable $session is not bound to a session object" ) );
        }
        } catch (SVNException e) {
            throw new XPathException(this, e.getMessage(), e);
        }
        return( BooleanValue.valueOf( returnValue ) );
    }
}
