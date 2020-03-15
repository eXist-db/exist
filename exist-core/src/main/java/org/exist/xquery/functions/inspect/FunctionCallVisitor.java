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
package org.exist.xquery.functions.inspect;

import org.exist.xquery.DefaultExpressionVisitor;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;

import java.util.*;

public class FunctionCallVisitor extends DefaultExpressionVisitor {

    private Set<FunctionSignature> functionCalls = new HashSet<>();

    public Set<FunctionSignature> getFunctionCalls() {
        return functionCalls;
    }

    @Override
    public void visitFunctionCall(FunctionCall call) {
        functionCalls.add(call.getSignature());
        // continue with the function arguments, but skip the body:
        // we're not interested in following function calls recursively
        for(int i = 0; i < call.getArgumentCount(); i++) {
            call.getArgument(i).accept(this);
        }
    }
}
