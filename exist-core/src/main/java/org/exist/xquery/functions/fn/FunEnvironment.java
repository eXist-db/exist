/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.fn;

import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

public class FunEnvironment extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(FunEnvironment.class);

    public final static FunctionSignature signature[] = {
        new FunctionSignature(
            new QName("available-environment-variables", Function.BUILTIN_FUNCTION_NS),
            "Returns a list of environment variable names.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, 
            "Returns a sequence of strings, being the names of the environment variables. User must be DBA.")
        ),
        new FunctionSignature(
            new QName("environment-variable", Function.BUILTIN_FUNCTION_NS),
            "Returns the value of a system environment variable, if it exists.",
            new SequenceType[] {
                new FunctionParameterSequenceType("name", Type.STRING,
                    Cardinality.EXACTLY_ONE, "Name of environment variable.")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "Corrensponding value of the environment variable, "
            + "if there is no environment variable with a matching name, the function returns the empty sequence. User must be DBA.")
        )
    };

    public FunEnvironment(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        if (!context.getSubject().hasDbaRole()) {
            final String txt = "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.";
            logger.error(txt);
            return Sequence.EMPTY_SEQUENCE;
        }

        if (isCalledAs("available-environment-variables")) {

            final Sequence result = new ValueSequence();

            final Map<String, String> env = context.getEnvironmentVariables();
            for (final String key : env.keySet()) {
                result.add(new StringValue(key));
            }

            return result;

        } else {

            if (args[0].isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }

            final String parameter = args[0].itemAt(0).getStringValue();

            final String value = context.getEnvironmentVariables().get(parameter);
            if (value == null) {
                return Sequence.EMPTY_SEQUENCE;
            }

            return new StringValue(value);
        }
    }
}
