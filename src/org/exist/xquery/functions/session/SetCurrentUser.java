/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.session;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.*;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.Optional;

/**
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class SetCurrentUser extends UserSwitchingBasicFunction {

    private static final Logger logger = LogManager.getLogger(SetCurrentUser.class);

    public final static FunctionSignature signature =
            new FunctionSignature(
                    new QName("set-current-user", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
                    "Change the user identity for the current HTTP session. Subsequent XQueries in the session will run with the " +
                            "new user identity.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("user-name", Type.STRING, Cardinality.EXACTLY_ONE, "The user name"),
                            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The password")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the user name and password represent a valid user"));

    public SetCurrentUser(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        //get the username and password parameters
        final String userName = args[0].getStringValue();
        final String passwd = args[1].getStringValue();

        //try and validate the user and password
        final SecurityManager security = context.getBroker().getBrokerPool().getSecurityManager();
        final Subject user;
        try {
            user = security.authenticate(userName, passwd);
        } catch (final AuthenticationException e) {
            logger.warn("Could not validate user " + userName + " [" + e.getMessage() + "]");
            return BooleanValue.FALSE;
        }

        //switch the user of the current broker
        switchUser(user);

        //validated user, store in session
        final SessionWrapper session = SessionFunction.getOrCreateSession(this, context,
                Optional.ofNullable(context.getHttpContext())
                        .map(XQueryContext.HttpContext::getSession)
        );
        session.setAttribute("user", userName);
        session.setAttribute("password", new StringValue(passwd));
        return BooleanValue.TRUE;
    }
}
