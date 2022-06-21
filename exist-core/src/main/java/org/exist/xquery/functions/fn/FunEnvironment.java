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
package org.exist.xquery.functions.fn;

import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.ISet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.AccessUtil;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunEnvironment extends BasicFunction {

    private static final Logger LOGGER = LogManager.getLogger(FunEnvironment.class);

    public final static FunctionSignature[] signature = {
            new FunctionSignature(
                    new QName("available-environment-variables", Function.BUILTIN_FUNCTION_NS),
                    "Returns a list of environment variable names.",
                    null,
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
                            "Returns a sequence of strings, being the names of the environment variables. " +
                                    "Only the Environment Variables that the calling user has been granted access to are returned (see conf.xml).")
            ),
            new FunctionSignature(
                    new QName("environment-variable", Function.BUILTIN_FUNCTION_NS),
                    "Returns the value of a system environment variable, if it exists.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("name", Type.STRING,
                                    Cardinality.EXACTLY_ONE, "Name of environment variable.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "Corresponding value of the environment variable, " +
                            "if there is no environment variable with a matching name, the function returns the empty sequence. " +
                            "User must have been granted access to the Environment Variable (see conf.xml).")
            )
    };

    public FunEnvironment(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final FnModule fnModule = (FnModule) getParentModule();
        final IMap<String, ISet<String>> environmentVariableAccessGroups = fnModule.getEnvironmentVariableAccessGroups();
        final IMap<String, ISet<String>> environmentVariableAccessUsers = fnModule.getEnvironmentVariableAccessUsers();

        if (isCalledAs("available-environment-variables")) {

            final Sequence result = new ValueSequence();

            final IMap<String, String> environmentVariables = context.getEnvironmentVariables();
            for (final String environmentVariableName : environmentVariables.keys()) {
                if (AccessUtil.isAllowedAccess(context.getEffectiveUser(), environmentVariableAccessGroups, environmentVariableAccessUsers, environmentVariableName)) {
                    result.add(new StringValue(environmentVariableName));
                }
            }

            return result;

        } else {

            final String environmentVariableName = args[0].itemAt(0).getStringValue();
            if (!AccessUtil.isAllowedAccess(context.getEffectiveUser(), environmentVariableAccessGroups, environmentVariableAccessUsers, environmentVariableName)) {
                final String txt = "Permission denied, calling user '" + context.getSubject().getName() + "' must be granted access to the Environment Variable: " + environmentVariableName + ".";
                LOGGER.error(txt);
                return Sequence.EMPTY_SEQUENCE;
            }

            final String value = context.getEnvironmentVariables().get(environmentVariableName, null);
            if (value == null) {
                return Sequence.EMPTY_SEQUENCE;
            }

            return new StringValue(value);
        }
    }
}
