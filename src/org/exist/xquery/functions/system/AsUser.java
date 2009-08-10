/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.security.User;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 */
public class AsUser extends Function {

    protected final static Logger logger = Logger.getLogger(AsUser.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
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
			new FunctionParameterSequenceType("result", Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the code block executed"));

    public AsUser(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
    	logger.info("Entering the " + SystemModule.PREFIX + ":as-user XQuery function");
        Sequence userSeq = getArgument(0).eval(contextSequence, contextItem);
        Sequence passwdSeq = getArgument(1).eval(contextSequence, contextItem);
        if (userSeq.isEmpty()) {
        	XPathException exception = new XPathException(this, "No user specified");
        	logger.error("No user specified, throwing an exception!", exception);
            throw exception;
        }
        String userName = userSeq.getStringValue();
        String passwd = passwdSeq.getStringValue();
        org.exist.security.SecurityManager security = context.getBroker().getBrokerPool().getSecurityManager();
        User user = security.getUser(userName);
		if (user == null) {
	        XPathException exception = new XPathException(this, "Authentication failed");
        	logger.error("Authentication failed for setting the user to [" + userName + "] because user does not exist, throwing an exception!", exception);
            throw exception;
        }
        if (user.validate(passwd)) {
            User oldUser = context.getBroker().getUser();
            try {
                logger.info("Setting the authenticated user to: [" + userName + "]");
                context.getBroker().setUser(user);
                return getArgument(2).eval(contextSequence, contextItem);
            } finally {
                logger.info("Returning the user to the original user: [" + oldUser.getName() + "]");
                context.getBroker().setUser(oldUser);
            }
        } else {
	        XPathException exception = new XPathException(this, "Authentication failed");
        	logger.error("Authentication failed for setting the user to [" + userName + "] because of bad password, throwing an exception!", exception);
            throw exception;
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        return getArgument(2).getDependencies();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#returnsType()
     */
    public int returnsType() {
        return getArgument(2).returnsType();
    }
}
