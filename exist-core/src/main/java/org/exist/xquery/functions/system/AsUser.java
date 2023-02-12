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
package org.exist.xquery.functions.system;

import com.evolvedbinary.j8fu.function.SupplierE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.system.SystemModule.functionSignature;

/**
 */
public class AsUser extends Function {

    private final static Logger logger = LogManager.getLogger(AsUser.class);

    private static String FS_AS_USER_NAME = "as-user";
    public final static FunctionSignature FS_AS_USER = functionSignature(
            FS_AS_USER_NAME,
            "A pseudo-function to execute a limited block of code as a different " +
            "user. The first argument is the name of the user, the second is the " +
            "password. If the user can be authenticated, the function will execute the " +
            "code block given in the third argument with the permissions of that user and" +
            "returns the result of the execution. Before the function completes, it switches " +
            "the current user back to the old user.",
            returnsOptMany(Type.ITEM, "the results of the code block executed"),
            param("username", Type.STRING, "The username of the user to run the code against"),
            optParam("password", Type.STRING, "The password of the user to run the code against"),
            optManyParam("code-block", Type.ITEM, "The code block to run as the identified user")
    );

    private static String FS_FUNCTION_AS_USER_NAME = "function-as-user";
    public final static FunctionSignature FS_FUNCTION_AS_USER = functionSignature(
            FS_FUNCTION_AS_USER_NAME,
            "A pseudo-function to execute a function as a different " +
                    "user. The first argument is the name of the user, the second is the " +
                    "password. If the user can be authenticated, the function will execute the " +
                    "function given in the third argument with the permissions of that user and" +
                    "returns the result of the execution. Before the function completes, it switches " +
                    "the current user back to the old user.",
            returnsOptMany(Type.ITEM, "the results of the code block executed"),
            param("username", Type.STRING, "The username of the user to run the code against"),
            optParam("password", Type.STRING, "The password of the user to run the code against"),
            param("function", Type.FUNCTION, "The zero arity function to run as the identified user")
    );

    public AsUser(final XQueryContext context, final FunctionSignature signature) {
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
            logger.error("Authentication failed for [{}] because of [{}].", username, e.getMessage(), exception);
            throw exception;
        }

        final SupplierE<Sequence, XPathException> function;
        if (isCalledAs(FS_AS_USER_NAME)) {
            final Expression codeBlock = getArgument(2);
            function = () -> codeBlock.eval(contextSequence, contextItem);
        } else if (isCalledAs(FS_FUNCTION_AS_USER_NAME)) {
            final FunctionReference functionArg = (FunctionReference) getArgument(2).eval(contextSequence, contextItem).itemAt(0);
            final int functionArgArity = functionArg.getSignature().getArgumentCount();
            if (functionArgArity != 0) {
                throw new XPathException(this, "$function argument must be a zero arity function, but found a function with arity: " + functionArgArity);
            }
            function = () -> functionArg.evalFunction(null, null, null);
        } else {
            throw new XPathException(this, "Unknown function: " + getSignature().getName());
        }

        if (logger.isTraceEnabled()) {
            logger.trace("Setting the effective user to: [{}]", username);
        }
        try {
            broker.pushSubject(user);
            return function.get();
        } finally {
            broker.popSubject();
            if (logger.isTraceEnabled()) {
                logger.trace("Returned the effective user to: [{}]", broker.getCurrentSubject());
            }
        }
    }

    @Override
    public int getDependencies() {
        return getArgument(2).getDependencies();
    }

    @Override
    public int returnsType() {
        return getArgument(2).returnsType();
    }
}
