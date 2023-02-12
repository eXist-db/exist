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
package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import static org.exist.xquery.ErrorCodes.XPDY0002;

public class ContextItemExpression extends LocationStep {

    private int returnType = Type.ITEM;

    public ContextItemExpression(final XQueryContext context) {
        // TODO: create class AnyItemTest (one private implementation found in saxon)
        super(context, Constants.SELF_AXIS, new AnyNodeTest());
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.addFlag(DOT_TEST);
        // set return type to static type to allow for index use in optimization step
        returnType = contextInfo.getStaticType();

        super.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextSequence == null && contextItem == null) {
            throw new XPathException(this, XPDY0002, "Nothing to select");
        }

        // function types in context item are returned unchanged
        if (contextItem != null &&
                Type.subTypeOf(contextItem.getType(), Type.FUNCTION)) {
            return contextItem.toSequence();
        }

        // function types in context sequence are returned unchanged
        if (contextSequence != null &&
                Type.subTypeOf(contextSequence.getItemType(), Type.FUNCTION)) {
            return contextSequence;
        }

        // handle everything else in super class
        return super.eval(contextSequence, contextItem);
    }

    @Override
    public int returnsType() {
        return returnType;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display(".");
    }

}
