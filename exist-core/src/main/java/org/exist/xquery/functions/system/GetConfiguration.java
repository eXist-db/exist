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
package org.exist.xquery.functions.system;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Returns the effective, in-memory {@code conf.xml} — as parsed and active,
 * not necessarily identical to the file on disk — with credential-shaped
 * values redacted by {@link ConfigurationRedactor}.
 */
public class GetConfiguration extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-configuration", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns the effective conf.xml as an in-memory element - the configuration as parsed " +
            "and active, not necessarily identical to the file on disk. Attribute and element " +
            "values whose local name looks like a credential (password, passwd, secret, " +
            "credential - case-insensitive) are replaced with '" + ConfigurationRedactor.REDACTED +
            "'; the structural shape is preserved. This function is only available to the DBA role.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                    "the effective configuration, with credential values redacted"));

    public GetConfiguration(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Only a DBA can call system:get-configuration()");
        }

        return ConfigurationRedactor.buildRedactedRoot(context);
    }
}
