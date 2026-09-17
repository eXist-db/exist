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
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.UserSwitchingBasicFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Functions to access the persistent login module.
 */
public class PersistentLoginFunctions extends UserSwitchingBasicFunction {
    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    PersistentLoginFn.REGISTER.getQName(),
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
                            new FunctionParameterSequenceType("onLogin", Type.FUNCTION_REFERENCE, Cardinality.ZERO_OR_ONE,
                                    "callback function to be called when the login succeeds")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result of the callback function or the empty sequence")
            ),
            new FunctionSignature(
                    PersistentLoginFn.LOGIN.getQName(),
                    "Try to log in the user based on the supplied token. If the login succeeds, the provided callback function " +
                            "is called with 4 arguments: $token as xs:string, $user as xs:string, $password as xs:string, $timeToLive as duration. " +
                            "$token will be a new token which can be used for the next request. The old token is deleted.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a valid one-time token"),
                            new FunctionParameterSequenceType("onLogin", Type.FUNCTION_REFERENCE, Cardinality.ZERO_OR_ONE,
                                    "callback function to be called when the login succeeds"),
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result of the callback function or the empty sequence")
            ),
            new FunctionSignature(
                    PersistentLoginFn.INVALIDATE.getQName(),
                    "Invalidate the supplied one-time token, so it can no longer be used to log in.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("token", Type.STRING, Cardinality.EXACTLY_ONE, "a valid one-time token")
                    },
                    new FunctionReturnSequenceType(Type.EMPTY, Cardinality.EXACTLY_ONE, "empty sequence")
            )
    };
    private AnalyzeContextInfo cachedContextInfo;

    public PersistentLoginFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    private static Sequence invalidate(Sequence[] args) throws XPathException {
        PersistentLogin.getInstance().invalidate(args[0].getStringValue());
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        switch (PersistentLoginFn.get(this)) {
            case REGISTER:
                return register(args);
            case LOGIN:
                return login(args);
            case INVALIDATE:
                return invalidate(args);
            default:
                throw new XPathException(this, ErrorCodes.ERROR, "Unknown function: " + getName());
        }
    }

    private Sequence register(Sequence[] args) throws XPathException {
        final String user = args[0].getStringValue();

        final String pass;
        if (args[1].isEmpty()) {
            pass = null;
        } else {
            pass = args[1].getStringValue();
        }

        final DurationValue timeToLive = (DurationValue) args[2].itemAt(0);

        try (FunctionReference callback = getCallBack(args[3])) {
            if (unauthenticated(user, pass)) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final PersistentLogin.LoginDetails details = PersistentLogin.getInstance().register(user, pass, timeToLive);
            return call(callback, null, details);
        }
    }

    private Sequence login(Sequence[] args) throws XPathException {
        final String token = args[0].getStringValue();
        try (FunctionReference callback = getCallBack(args[1])) {
            final PersistentLogin.LoginDetails data = PersistentLogin.getInstance().lookup(token);

            if (data == null || unauthenticated(data.getUser(), data.getPassword())) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return call(callback, token, data);
        }
    }

    private boolean unauthenticated(final String user, final String pass) {
        try {
            final SecurityManager sm = BrokerPool.getInstance().getSecurityManager();
            final Subject subject = sm.authenticate(user, pass);

            //switch the user of the current broker
            switchUser(subject);

            return false;
        } catch (final AuthenticationException | EXistException e) {
            return true;
        }
    }

    private Sequence call(@Nullable final FunctionReference func, final String oldToken, final PersistentLogin.LoginDetails details) throws XPathException {
        if (func == null) return Sequence.EMPTY_SEQUENCE;
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

    private @Nullable FunctionReference getCallBack(final Sequence arg) {
        if (arg.isEmpty()) {
            return null;
        }
        return (FunctionReference) arg.itemAt(0);
    }

    private enum PersistentLoginFn {
        REGISTER("register"),
        LOGIN("login"),
        INVALIDATE("invalidate");

        final static Map<QName, PersistentLoginFn> lookup = new HashMap<>();

        static {
            for (PersistentLoginFn persistentLoginFn : EnumSet.allOf(PersistentLoginFn.class)) {
                lookup.put(persistentLoginFn.getQName(), persistentLoginFn);
            }
        }

        private final QName qname;

        PersistentLoginFn(String name) {
            qname = new QName(name, PersistentLoginModule.NAMESPACE, PersistentLoginModule.PREFIX);
        }

        static PersistentLoginFn get(Function f) {
            return lookup.get(f.getName());
        }

        public QName getQName() {
            return qname;
        }
    }
}
