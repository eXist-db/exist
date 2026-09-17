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
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * An XPath-style accessor into the effective (redacted) configuration, for
 * targeted lookups without returning the whole {@code system:get-configuration()}
 * document — e.g. {@code system:get-configuration-property("db-connection/@cacheSize")}.
 *
 * <p>Redaction happens once, up front, via {@link ConfigurationRedactor#buildRedactedRoot}
 * — $path is then evaluated by eXist's own XQuery engine against the already-redacted
 * root, so a matched credential-shaped node's value is "[redacted]" before this function
 * ever sees it (no separate post-hoc redaction check needed here).
 */
public class GetConfigurationProperty extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-configuration-property", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Evaluates $path as an XPath/XQuery expression against the effective configuration " +
            "(the same, already-redacted document system:get-configuration() returns) and returns " +
            "the string value of each matched item. This function is only available to the DBA role.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE,
                            "an XPath/XQuery expression, e.g. 'db-connection/@cacheSize'")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
                    "the string value of each item matched by $path"));

    public GetConfigurationProperty(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Only a DBA can call system:get-configuration-property()");
        }

        final String path = args[0].getStringValue();
        final Sequence redactedRoot = ConfigurationRedactor.buildRedactedRoot(context);

        final XQuery xqueryService = context.getBroker().getBrokerPool().getXQueryService();
        final Sequence matches;
        try {
            matches = xqueryService.execute(context.getBroker(), path, redactedRoot);
        } catch (final PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, "Invalid XPath expression: " + e.getMessage());
        }

        final ValueSequence resultSeq = new ValueSequence();
        final SequenceIterator it = matches.iterate();
        while (it.hasNext()) {
            resultSeq.add(new StringValue(this, it.nextItem().getStringValue()));
        }
        return resultSeq;
    }
}
