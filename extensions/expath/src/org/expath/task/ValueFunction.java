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

import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.function.Function;

import static org.exist.xquery.FunctionDSL.*;
import static org.expath.task.TaskModule.functionSignature;
import static org.expath.task.TaskType.createMonadicTask;
import static org.expath.task.Util.sequenceOf;

public class ValueFunction extends BasicFunction {

    private static final String FS_VALUE_NAME = "value";
    static final FunctionSignature FS_VALUE = functionSignature(
            FS_VALUE_NAME,
            "Creates a Task from a \"pure\" value.",
            returns(Type.MAP, "A Task which when executed returns the pure value."),
            optManyParam("v", Type.ITEM, "A pure value")
    );

    public ValueFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence v = args[0];
        return createMonadicTask(context, realWorld ->
            sequenceOf(realWorld, v)
        );
    }
}
