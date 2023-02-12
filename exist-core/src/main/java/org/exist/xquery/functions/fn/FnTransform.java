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

import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.transform.Transform;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * Implementation of fn:transform.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:alan@evolvedbinary.com">Alan Paxton</a>
 */
public class FnTransform extends BasicFunction {

    private static final String FS_TRANSFORM_NAME = "transform";
    static final FunctionSignature FS_TRANSFORM = functionSignature(
            FnTransform.FS_TRANSFORM_NAME,
            "Invokes a transformation using a dynamically-loaded XSLT stylesheet.",
            returnsOptMany(Type.MAP_ITEM, "The result of the transformation is returned as a map. " +
                    "There is one entry in the map for the principal result document, and one for each " +
                    "secondary result document. The key is a URI in the form of an xs:string value. " +
                    "The key for the principal result document is the base output URI if specified, or " +
                    "the string \"output\" otherwise. The key for secondary result documents is the URI of the " +
                    "document, as an absolute URI. The associated value in each entry depends on the requested " +
                    "delivery format. If the delivery format is document, the value is a document node. If the " +
                    "delivery format is serialized, the value is a string containing the serialized result."),
            param("options", Type.MAP_ITEM, "The inputs to the transformation are supplied in the form of a map")
    );

    private final Transform transform;

    public FnTransform(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
        this.transform = new Transform(context, this);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        return transform.eval(args, contextSequence);
    }
}
