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
package org.exist.xquery.functions.response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Date;

import javax.annotation.Nonnull;
import javax.xml.datatype.Duration;


/**
 * Set's a HTTP Cookie on the HTTP Response.
 *
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 * @author José María Fernández (jmfg@users.sourceforge.net)
 * @see org.exist.xquery.Function
 */
public class SetCookie extends StrictResponseFunction {
    private static final Logger logger = LogManager.getLogger(SetCookie.class);
    private static final FunctionParameterSequenceType NAME_PARAM = new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The cookie name");
    private static final FunctionParameterSequenceType VALUE_PARAM = new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The cookie value");
    private static final FunctionParameterSequenceType MAX_AGE_PARAM = new FunctionParameterSequenceType("max-age", Type.DURATION, Cardinality.ZERO_OR_ONE, "The xs:duration of the cookie");
    private static final FunctionParameterSequenceType SECURE_PARAM = new FunctionParameterSequenceType("secure-flag", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "The flag for whether the cookie is to be secure (i.e., only transferred using HTTPS)");
    private static final FunctionParameterSequenceType DOMAIN_PARAM = new FunctionParameterSequenceType("domain", Type.STRING, Cardinality.ZERO_OR_ONE, "The cookie domain");
    private static final FunctionParameterSequenceType PATH_PARAM = new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_ONE, "The cookie path");

    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    "Sets a HTTP Cookie on the HTTP Response.",
                    new SequenceType[]{NAME_PARAM, VALUE_PARAM},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
            new FunctionSignature(
                    new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    "Sets a HTTP Cookie on the HTTP Response.",
                    new SequenceType[]{NAME_PARAM, VALUE_PARAM, MAX_AGE_PARAM, SECURE_PARAM},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)),
            new FunctionSignature(
                    new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
                    "Sets a HTTP Cookie on the HTTP Response.",
                    new SequenceType[]{NAME_PARAM, VALUE_PARAM, MAX_AGE_PARAM, SECURE_PARAM, DOMAIN_PARAM, PATH_PARAM},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE))
    };

    public SetCookie(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final ResponseWrapper response)
            throws XPathException {

        //get parameters
        final String name = args[0].getStringValue();
        final String value = args[1].getStringValue();

        final int maxAge;
        final Sequence secureSeq, domainSeq, pathSeq;
        if (getArgumentCount() > 2) {
            final Sequence ageSeq = args[2];
            secureSeq = args[3];

            if (!ageSeq.isEmpty()) {
                final Duration duration = ((DurationValue) ageSeq.itemAt(0)).getCanonicalDuration();
                maxAge = (int) (duration.getTimeInMillis(new Date(System.currentTimeMillis())) / 1000L);
            } else {
                maxAge = -1;
            }

            if (getArgumentCount() > 4) {
                domainSeq = args[4];
                pathSeq = args[5];
            } else {
                domainSeq = Sequence.EMPTY_SEQUENCE;
                pathSeq = Sequence.EMPTY_SEQUENCE;
            }
        } else {
            secureSeq = Sequence.EMPTY_SEQUENCE;
            domainSeq = Sequence.EMPTY_SEQUENCE;
            pathSeq = Sequence.EMPTY_SEQUENCE;
            maxAge = -1;
        }

        //set response header
        switch (getArgumentCount()) {

            case 2: {
                response.addCookie(name, value);
                break;
            }

            case 4: {
                if (secureSeq.isEmpty()) {
                    response.addCookie(name, value, maxAge);
                } else {
                    response.addCookie(name, value, maxAge, ((BooleanValue) secureSeq.itemAt(0)).effectiveBooleanValue());
                }
                break;
            }

            case 6: {
                boolean secure = false;
                String domain = null;
                String path = null;
                if (!secureSeq.isEmpty()) {
                    secure = ((BooleanValue) secureSeq.itemAt(0)).effectiveBooleanValue();
                }
                if (!domainSeq.isEmpty()) {
                    domain = domainSeq.itemAt(0).getStringValue();
                }
                if (!pathSeq.isEmpty()) {
                    path = pathSeq.itemAt(0).getStringValue();
                }
                response.addCookie(name, value, maxAge, secure, domain, path);
                break;
            }
        }
        return Sequence.EMPTY_SEQUENCE;
    }
}
