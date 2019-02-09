/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-09 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.util.UUIDGenerator;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wessels
 * @author Loren Cahlander
 */
public class UUID extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(UUID.class);
    
    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("uuid", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Generate a version 4 (random) universally unique identifier (UUID) string, e.g. 154ad200-9c79-44f3-8cff-9780d91552a6",
                    FunctionSignature.NO_ARGS,
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "a generated UUID string")
                ),

            new FunctionSignature (
                    new QName("uuid", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Generate a version 3 universally unique identifier (UUID) string, e.g. 2b92ddb6-8e4e-3891-b519-afa1609ced73",
                    new SequenceType[]{
                        new FunctionParameterSequenceType("name", Type.ITEM, Cardinality.EXACTLY_ONE,
                            "The input value for UUID calculation."),
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "a generated UUID string")
                )
    };

    public UUID(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        final Sequence result = new ValueSequence();

        // Check input parameters
        if (args.length == 0) {
            final String uuid = UUIDGenerator.getUUIDversion4();
            result.add(new StringValue(uuid));

        } else if (args.length == 1) {
            final String parameter = args[0].getStringValue();
            final String uuid = UUIDGenerator.getUUIDversion3(parameter);
            result.add(new StringValue(uuid));

        } else {
            logger.error("Not a supported number of parameters");
            throw new XPathException("Not a supported number of parameters");
        }

        return result;
    }
}
