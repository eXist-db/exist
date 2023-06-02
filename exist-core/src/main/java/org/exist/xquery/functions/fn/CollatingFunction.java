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

import com.ibm.icu.text.Collator;
import org.exist.xquery.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Base class for functions accepting an optional collation argument. 
 * @author wolf
 */
public abstract class CollatingFunction extends Function {

    public final static String THIRD_REL_COLLATION_ARG_EXAMPLE =
        "The third argument $collation-uri is either: " +
        "1) the full URI e.g. \"http://www.w3.org/2013/collation/UCA?lang=en;strength=secondary\", or " +
        "2) relative where you only need to " +
        "specify the last part of a valid full collation-uri, e.g. " + 
        "\"?lang=sv-SE\", " +
        "\"lang=sv-SE;strength=primary;decomposition=standard\" " +
        "or \"swedish\".";

    public CollatingFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    protected Collator getCollator(Sequence contextSequence, Item contextItem,
            int arg) throws XPathException {
        if (getSignature().getArgumentCount() == arg) {
            final String collationURI = getArgument(arg - 1).eval(contextSequence,
                contextItem).getStringValue();
            return context.getCollator(collationURI, ErrorCodes.FOCH0002);
        } else {
            return context.getDefaultCollator();
        }
    }
}
