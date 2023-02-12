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

package org.exist.xquery.functions.util;

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.inspect.InspectFunctionHelper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returns;
import static org.exist.xquery.functions.util.UtilModule.functionSignature;

public class InspectFunction extends BasicFunction {

    public static final String FN_INSPECT_FUNCTION_NAME = "inspect-function";
    public static final FunctionSignature FN_INSPECT_FUNCTION = functionSignature(
            FN_INSPECT_FUNCTION_NAME,
            "Returns an XML fragment describing the function referenced by the passed function item.",
            returns(Type.NODE, "the signature of the function"),
            param("function", Type.FUNCTION, "The function item to inspect")
    );

    public InspectFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
        final FunctionSignature sig = ref.getSignature();
        try {
            context.pushDocumentContext();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final int nodeNr = InspectFunctionHelper.generateDocs(sig, null, builder);
            return builder.getDocument().getNode(nodeNr);
        } finally {
            context.popDocumentContext();
        }
    }
}
