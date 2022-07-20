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

import java.nio.file.Path;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Return the eXist home.
 *
 * @author Dannes Wessels
 */
public class GetExistHome extends BasicFunction {
    
    protected final static Logger logger = LogManager.getLogger(GetExistHome.class);

    public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("get-exist-home", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns the eXist home location.",
            FunctionSignature.NO_ARGS,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the path to the eXist home"));
    
    public GetExistHome(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final Optional<Path> existHome = context.getBroker().getConfiguration().getExistHome();
        final Expression expression = this;
        return existHome.<Sequence>map(h -> new StringValue(expression, h.toAbsolutePath().toString())).orElse(Sequence.EMPTY_SEQUENCE);
    }
}
