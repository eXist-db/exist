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
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.expath.task.promise.Promise;

import static org.expath.task.Util.head;
import static org.expath.task.Util.sequenceOf;

public class TaskType extends AbstractTaskMapType {

    private final FunctionE<RealWorld, Sequence, XPathException> applyFn;

    public static TaskType createMonadicTask(final XQueryContext context, final FunctionE<RealWorld, Sequence, XPathException> applyFn) {
        return new TaskType(context, applyFn);
    }

    private TaskType(final XQueryContext context, final FunctionE<RealWorld, Sequence, XPathException> applyFn) {
        super(context);
        this.applyFn = applyFn;
    }

    @Override
    protected Sequence apply(final RealWorld readWorld) throws XPathException {
        return applyFn.apply(readWorld);
    }

    @Override
    protected TaskType bind(final XQueryContext context, final FunctionE<Sequence, TaskType, XPathException> binder) {
        final FunctionE<RealWorld, Sequence, XPathException> boundApplyFn = realWorld -> {
            final Sequence ioRes = applyFn.apply(realWorld);
            return binder.apply(Util.tail(ioRes)).applyFn.apply((RealWorld) head(ioRes));
        };

        return createMonadicTask(context, boundApplyFn);
    }

    @Override
    protected TaskType then(final XQueryContext context, final TaskType next) {
        final FunctionE<RealWorld, Sequence, XPathException> thenApplyFn = realWorld -> {
            final Sequence ioRes = applyFn.apply(realWorld);
            /* NOTE: the result given by head($ioRes)
               is not needed by `then`, so we can ignore it */
            return next.applyFn.apply((RealWorld) head(ioRes));
        };

        return createMonadicTask(context, thenApplyFn);
    }

    @Override
    protected TaskType async(final XQueryContext context) {
        final FunctionE<RealWorld, Sequence, XPathException> asyncApplyFn = realWorld -> {

            // create a promise (this is our asynchronous execution)
            final Promise p = new Promise<Sequence, XPathException>((resolve, reject) -> {
                try {
                    resolve.accept(applyFn.apply(realWorld));
                } catch (final XPathException e) {
                    reject.accept(e);
                }
            });

            // this is our Async
            final Item asyncA = new AsyncType(context, p);

            return sequenceOf(realWorld, asyncA);
        };

        return createMonadicTask(context, asyncApplyFn);
    }

    @Override
    protected Sequence runUnsafe(final RealWorld realWorld) throws XPathException {
        return Util.tail(
                applyFn.apply(realWorld)
        );
    }
}
