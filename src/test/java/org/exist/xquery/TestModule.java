/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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

package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.List;
import java.util.Map;

/**
 * Used by {@link CleanupTest}.
 */
public class TestModule extends AbstractInternalModule {

    private final static String MODULE_NS = "http://exist-db.org/test";

    private final static FunctionDef[] functions = {
            new FunctionDef(TestFunction.signature, TestFunction.class)
    };

    public TestModule(Map<String, List<?>> parameters) throws XPathException {
        super(functions, parameters);
    }

    @Override
    public String getNamespaceURI() {
        return MODULE_NS;
    }

    @Override
    public String getDefaultPrefix() {
        return "t";
    }

    @Override
    public String getDescription() {
        return null;
    }

    @Override
    public String getReleaseVersion() {
        return null;
    }

    public static class TestFunction extends BasicFunction {

        public final static FunctionSignature signature =
                new FunctionSignature(
                        new QName("test", MODULE_NS),
                        "Test function",
                        new SequenceType[] {
                        },
                        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE,
                                "return value")
                );

        // used to check if resetState is called on the function
        public boolean dummyProperty = false;

        public TestFunction(XQueryContext context) {
            super(context, signature);
        }

        @Override
        public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
            dummyProperty = true;
            final Variable var = context.resolveVariable(new QName("VAR", MODULE_NS, "t"));
            return var.getValue();
        }

        @Override
        public void resetState(boolean postOptimization) {
            super.resetState(postOptimization);
            if (!postOptimization) {
                dummyProperty = false;
            }
        }
    }
}