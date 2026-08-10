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
import org.exist.management.client.JMXTokenProvider;
import org.exist.management.client.JMXtoXML;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Return the JMXServlet access token, i.e. the same secret {@code JMXServlet} accepts via its
 * {@code token} request parameter for requests that do not originate from localhost.
 *
 * @author eXist-db authors
 */
public class GetJmxToken extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
            new QName("get-jmx-token", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns the JMXServlet access token, i.e. the secret accepted via the 'token' request " +
            "parameter of /exist/status (JMXServlet) as an alternative to a request originating " +
            "from localhost. Resolved via the same DiskUsage MBean-based data directory lookup " +
            "JMXServlet itself uses, so the two never diverge. Returns the empty sequence if JMX, " +
            "or the token, could not be resolved. This function is only available to the DBA role.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                    "the JMXServlet access token, or the empty sequence if it could not be resolved"));

    public GetJmxToken(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Only a DBA can call system:get-jmx-token()");
        }

        final JMXtoXML client = new JMXtoXML();
        client.connect();

        final JMXTokenProvider tokenProvider = new JMXTokenProvider(client);
        return tokenProvider.getToken()
                .<Sequence>map(token -> new StringValue(this, token))
                .orElse(Sequence.EMPTY_SEQUENCE);
    }
}
