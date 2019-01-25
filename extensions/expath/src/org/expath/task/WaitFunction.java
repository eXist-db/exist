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
import org.expath.task.promise.Promise;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returns;
import static org.expath.task.TaskModule.functionSignature;
import static org.expath.task.TaskType.createMonadicTask;
import static org.expath.task.Util.sequenceOf;
import static org.expath.task.Util.tail;

public class WaitFunction extends BasicFunction {

    private static final String FS_WAIT_NAME = "wait";
    static final FunctionSignature FS_WAIT = functionSignature(
            FS_WAIT_NAME,
            "Given an Async this function will extract its value and return a Task of the value.",
            returns(Type.MAP, "A new Task representing the result of the completed asynchronous computation"),
            param("async", Type.FUNCTION_REFERENCE, "the asynchronous computation")
    );

    public WaitFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final AsyncType async = (AsyncType)args[0].itemAt(0);
        return createMonadicTask(context, realWorld -> {

            // let's wait on the Promise
            final Promise<Sequence, XPathException> promise = async.getPromise();
            final Sequence asyncRes = promise.await();

            return sequenceOf(realWorld, tail(asyncRes));
        });
    }
}
