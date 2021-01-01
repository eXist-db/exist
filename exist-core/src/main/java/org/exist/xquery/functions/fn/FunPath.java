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

import org.exist.xquery.*;
import org.exist.xquery.value.*;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.returnsOpt;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * @author Dannes
 */
public class FunPath extends Function {

    private static final FunctionParameterSequenceType FS_PARAM_NODE = optParam("arg", Type.NODE, "The node.");

    private static final String FS_PATH = "path";
    private static final String FS_DESCRIPTION = "Returns a path expression that can be used to select the supplied node relative to the root of its containing document.";
    private static final String FS_RETURN_DESCRIPTION = "The path of the node";

    static final FunctionSignature FS_PATH_0 = functionSignature(
            FS_PATH,
            FS_DESCRIPTION,
            returnsOpt(Type.STRING, FS_RETURN_DESCRIPTION)
    );

    static final FunctionSignature FS_PATH_1 = functionSignature(
            FS_PATH,
            FS_DESCRIPTION,
            returnsOpt(Type.STRING, FS_RETURN_DESCRIPTION),
            FS_PARAM_NODE
    );

    public FunPath(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        // Get sequence from contextItem or from context Sequence
        final Sequence seq = (contextItem != null)
                ? contextItem.toSequence()
                : getArgument(0).eval(contextSequence, contextItem);


        // If $arg is the empty sequence, the function returns the empty sequence.
        if(seq.isEmpty()){
            return Sequence.EMPTY_SEQUENCE;
        }

        // Default response
        Sequence result = Sequence.EMPTY_SEQUENCE;

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }
}
