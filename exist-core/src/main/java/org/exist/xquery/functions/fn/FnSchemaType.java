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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * fn:schema-type($name as xs:QName) as map(*)
 *
 * Returns a schema-type-record for the named type, with the same structure
 * as fn:atomic-type-annotation: name, is-simple, variety, base-type(),
 * primitive-type(), matches(), constructor().
 *
 * Delegates to {@link FnTypeAnnotation} for the full record chain.
 */
public class FnSchemaType extends BasicFunction {

    public static final FunctionSignature FN_SCHEMA_TYPE = new FunctionSignature(
            new QName("schema-type", Function.BUILTIN_FUNCTION_NS),
            "Returns a map describing the named schema type.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("name", Type.QNAME,
                            Cardinality.EXACTLY_ONE, "The QName of the type")
            },
            new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                    "A schema-type-record describing the type"));

    public FnSchemaType(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final QNameValue qnameVal = (QNameValue) args[0].itemAt(0);
        final QName typeName = qnameVal.getQName();

        // Look up the type in eXist's type system
        final int typeCode = Type.getType(typeName);
        if (typeCode == Type.ITEM && !"item".equals(typeName.getLocalPart())) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Unknown schema type: " + typeName);
        }

        // Delegate to FnTypeAnnotation for the full schema-type-record
        // with base-type, primitive-type, matches, constructor
        final boolean isSimple = typeCode != Type.ANY_TYPE && typeCode != Type.UNTYPED;
        final FnTypeAnnotation helper = new FnTypeAnnotation(context,
                FnTypeAnnotation.FN_ATOMIC_TYPE_ANNOTATION);
        return helper.buildSchemaTypeRecord(typeCode, isSimple);
    }
}
