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

import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.persistent.NodeHandle;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.expath.task.promise.Promise;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Properties;

import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.expath.task.TaskModule.functionSignature;

public class AsyncType extends FunctionReference {

    private final Promise<Sequence, XPathException> promise;

    public AsyncType(final XQueryContext context, final Promise promise) {
        super (new FunctionCall(context, new AsyncTypeFunction(context, promise)));
        this.promise = promise;
    }

    public Promise<Sequence, XPathException> getPromise() {
        return promise;
    }

    private static class AsyncTypeFunction extends UserDefinedFunction {
        private final Promise promise;

        public AsyncTypeFunction(final XQueryContext context, final Promise promise) {
            super(context, functionSignature(
                    "Async",
                    "this is really an Async",
                    returnsOptMany(Type.ITEM)
            ));
            this.promise = promise;
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            if (visited) {
                return;
            }
            visited = true;
        }
    }
}
