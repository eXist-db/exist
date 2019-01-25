/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.expath.task;

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returns;
import static org.expath.task.TaskModule.functionSignature;
import static org.expath.task.TaskType.createMonadicTask;
import static org.expath.task.Util.sequenceOf;

public class OfFunction extends BasicFunction {

    private static final String FS_OF_NAME = "of";
    static final FunctionSignature FS_OF = functionSignature(
            FS_OF_NAME,
            "Creates a Task from a function",
            returns(Type.MAP, "A Task which when executed returns the pure value."),
            param("f", Type.FUNCTION_REFERENCE, "A zero arity function")
    );

    public OfFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final FunctionReference xdmFunctionReference = (FunctionReference)args[0].itemAt(0);
        return createMonadicTask(context, realWorld -> {
            final Sequence result = xdmFunctionReference.evalFunction(contextSequence, null, new Sequence[0]);
            return sequenceOf(realWorld, result);
        });
    }
}
