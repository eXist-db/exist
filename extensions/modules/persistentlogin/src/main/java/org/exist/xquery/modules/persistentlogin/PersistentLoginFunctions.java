/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.persistentlogin;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * Functions to access the persistent login module.
 */
public class PersistentLoginFunctions extends UserSwitchingBasicFunction {

    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("register", PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX),
                    "Try to log in the user and create a one-time login token. The token can be stored to a cookie and used to log in " +
                            "(via the login function) as the same user without " +
                            "providing credentials. However, for security reasons the token will be valid only for " +
                            "the next request to the login function and is deleted afterwards. " +
                            "If the user is valid and the token could be generated, the " +
                            "supplied callback function is called with 4 arguments: $token as xs:string, $user as xs:string, $password as xs:string, " +
                            "$timeToLive as xs:duration.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("user", Type.STRING, Cardinality.EXACTLY_ONE, "user name"),
                            new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "password"),
                            new FunctionParameterSequenceType("timeToLive", Type.DURATION, Cardinality.EXACTLY_ONE, "duration for which the user is remembered"),
                            new FunctionParameterSequenceType("onLogin", Type.FUNCTION, Cardinality.ZERO_OR_ONE,
                                    "callback function to be called when the login succeeds")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result of the callback function or the empty sequence")
            ),
            new FunctionSignature(
                    new QName("login", PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX),
                    "Try to log in the user based on the supplied token. If the login succeeds, the provided callback function " +
                            "is called with 4 arguments: $token as xs:string, $user as xs:string, $password as xs:string, $timeToLive as duration. " +
                            "$token will be a new token which can be used for the next request. The old token is deleted.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a valid one-time token"),
                            new FunctionParameterSequenceType("onLogin", Type.FUNCTION, Cardinality.ZERO_OR_ONE,
                                    "callback function to be called when the login succeeds"),
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result of the callback function or the empty sequence")
            ),
            new FunctionSignature(
                    new QName("invalidate", PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX),
                    "Invalidate the supplied one-time token, so it can no longer be used to log in.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a valid one-time token")
                    },
                    new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EXACTLY_ONE, "empty sequence")
            )
    };

    private AnalyzeContextInfo cachedContextInfo;

    public PersistentLoginFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        this.cachedContextInfo = new AnalyzeContextInfo(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("register")) {
            final String user = args[0].getStringValue();
            final String pass;
            if (!args[1].isEmpty()) {
                pass = args[1].getStringValue();
            } else {
                pass = null;
            }
            final DurationValue timeToLive = (DurationValue) args[2].itemAt(0);
            final FunctionReference callback;
            if (!args[3].isEmpty()) {
                callback = (FunctionReference) args[3].itemAt(0);
            } else {
                callback = null;
            }
            try {
                return register(user, pass, timeToLive, callback);
            } finally {
                if (callback != null) {
                    callback.close();
                }
            }
        } else if (isCalledAs("login")) {
            final String token = args[0].getStringValue();
            final FunctionReference callback;
            if (!args[1].isEmpty()) {
                callback = (FunctionReference) args[1].itemAt(0);
            } else {
                callback = null;
            }
            try {
                return authenticate(token, callback);
            } finally {
                if (callback != null) {
                    callback.close();
                }
            }
        } else {
            PersistentLogin.getInstance().invalidate(args[0].getStringValue());
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    private Sequence register(final String user, final String pass, final DurationValue timeToLive, final FunctionReference callback) throws XPathException {
        if (login(user, pass)) {
            final PersistentLogin.LoginDetails details = PersistentLogin.getInstance().register(user, pass, timeToLive);
            return callback(callback, null, details);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence authenticate(final String token, final FunctionReference callback) throws XPathException {
        final PersistentLogin.LoginDetails data = PersistentLogin.getInstance().lookup(token);

        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        if (login(data.getUser(), data.getPassword())) {
            return callback(callback, token, data);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private boolean login(final String user, final String pass) throws XPathException {
        try {
            final SecurityManager sm = BrokerPool.getInstance().getSecurityManager();
            final Subject subject = sm.authenticate(user, pass);

            //switch the user of the current broker
            switchUser(subject);

            return true;
        } catch (final AuthenticationException | EXistException e) {
            return false;
        }
    }

    private Sequence callback(final FunctionReference func, final String oldToken, final PersistentLogin.LoginDetails details) throws XPathException {
        final Sequence[] args = new Sequence[4];
        final String newToken = details.toString();

        if (oldToken != null && oldToken.equals(newToken)) {
            args[0] = Sequence.EMPTY_SEQUENCE;
        } else {
            args[0] = new StringValue(this, newToken);
        }
        args[1] = new StringValue(this, details.getUser());
        args[2] = new StringValue(this, details.getPassword());
        args[3] = details.getTimeToLive();

        func.analyze(cachedContextInfo);
        return func.evalFunction(null, null, args);
    }
}
