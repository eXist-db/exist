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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FnDefaultLanguage extends BasicFunction {

    public static final FunctionSignature FS_DEFAULT_LANGUAGE = FunctionDSL.functionSignature(
            new QName("default-language", Function.BUILTIN_FUNCTION_NS),
            "Returns the xs:language that is " +
                    "the value of the default language property from the dynamic context " +
                    "during the evaluation of a query or transformation in which " +
                    "fn:default-language() is executed.",
            FunctionDSL.returns(Type.LANGUAGE, Cardinality.EXACTLY_ONE, "the default language within query execution time span"));

    public FnDefaultLanguage(final XQueryContext context) {
        super(context, FS_DEFAULT_LANGUAGE);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        return new StringValue(this, context.getDefaultLanguage());
    }

}
