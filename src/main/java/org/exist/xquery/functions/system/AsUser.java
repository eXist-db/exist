/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 */
public class AsUser extends Function {

    private final static Logger logger = LogManager.getLogger(AsUser.class);

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("as-user", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "A pseudo-function to execute a limited block of code as a different " +
        "user. The first argument is the name of the user, the second is the " +
        "password. If the user can be authenticated, the function will execute the " +
        "code block given in the third argument with the permissions of that user and" +
        "returns the result of the execution. Before the function completes, it switches " +
        "the current user back to the old user.",
        new SequenceType[] {
            new FunctionParameterSequenceType("username", Type.STRING, Cardinality.EXACTLY_ONE, "The username of the user to run the code against"),
            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The password of the user to run the code against"),
            new FunctionParameterSequenceType("code-block", Type.ITEM, Cardinality.ZERO_OR_MORE, "The code block to run as the identified user")
        },
        new FunctionParameterSequenceType("result", Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the code block executed")
    );

    public AsUser(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
    	logger.debug("Entering the " + SystemModule.PREFIX + ":as-user XQuery function");

        final DBBroker broker = context.getBroker();

        final Sequence usernameResult = getArgument(0).eval(contextSequence, contextItem);
        if(usernameResult.isEmpty()) {
            final XPathException exception = new XPathException(this, "No user specified");
            logger.error("No user specified, throwing an exception!", exception);
            throw exception;
        }
        
        final Sequence password = getArgument(1).eval(contextSequence, contextItem);
        final String username = usernameResult.getStringValue();
        
        final SecurityManager sm = broker.getBrokerPool().getSecurityManager();
        Subject user;
        try {
            user = sm.authenticate(username, password.getStringValue());
        } catch(final AuthenticationException e) {
            final XPathException exception = new XPathException(this, "Authentication failed", e);
            logger.error("Authentication failed for [" + username + "] because of [" + e.getMessage() + "].", exception);
            throw exception;
        }

        logger.info("Setting the effective user to: [" + username + "]");
        try {
            broker.pushSubject(user);
            return getArgument(2).eval(contextSequence, contextItem);
        } finally {
            broker.popSubject();
            logger.info("Returned the effective user to: [" + broker.getCurrentSubject() + "]");
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    @Override
    public int getDependencies() {
        return getArgument(2).getDependencies();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#returnsType()
     */
    @Override
    public int returnsType() {
        return getArgument(2).returnsType();
    }
}
