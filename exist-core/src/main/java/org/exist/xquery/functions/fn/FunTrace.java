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

import org.apache.commons.lang3.StringUtils;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * @author Dannes Wessels
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunTrace extends BasicFunction {


    private static final FunctionParameterSequenceType FS_PARAM_VALUE = optManyParam("value", Type.ITEM, "The values");
    private static final FunctionParameterSequenceType FS_PARAM_LABEL = param("label", Type.STRING, "The label in the log file");

    private static final String FS_TRACE_NAME = "trace";

    static final FunctionSignature FS_TRACE1 = functionSignature(
            FS_TRACE_NAME,
            "This function is intended to be used in debugging queries by "
                    + "providing a trace of their execution. The input $value is "
                    + "returned, unchanged, as the result of the function. "
                    + "In addition, the inputs $value is serialized with adaptive settings "
                    + "and is written into the eXist log files.",
            returnsOptMany(Type.ITEM, "The unlabelled $value in the log"),
            FS_PARAM_VALUE
    );

    static final FunctionSignature FS_TRACE2 = functionSignature(
            FS_TRACE_NAME,
            "This function is intended to be used in debugging queries by "
                    + "providing a trace of their execution. The input $value is "
                    + "returned, unchanged, as the result of the function. "
                    + "In addition, the inputs $value is serialized with adaptive settings "
                    + "and is written together with $label into the eXist log files.",
            returnsOptMany(Type.ITEM, "The labelled $value in the log"),
            FS_PARAM_VALUE,
            FS_PARAM_LABEL
    );

    public FunTrace(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

/*
 * (non-Javadoc)
 * @see org.exist.xquery.BasicFunction#eval(Sequence[], Sequence)
 */
public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

    // Get value for label, default to "-"
    final String label = (args.length == 2) ? StringUtils.defaultIfBlank(args[1].getStringValue(), "-") : "-";

    final Sequence result;

    if (args[0].isEmpty()) {
        // Write to log
        LOG.debug("{} [{}] [{}]: {}", label, "-", Type.getTypeName(Type.EMPTY_SEQUENCE), "-");
        result = Sequence.EMPTY_SEQUENCE;

        } else {
            // Copy all Items from input to output sequence
            result = new ValueSequence();

            int position = 0;

            // Force adaptive serialization
            final Properties props = new Properties();
            props.setProperty(OutputKeys.METHOD, "adaptive");

            for (final SequenceIterator i = args[0].iterate(); i.hasNext();) {

                // Get item
                final Item next = i.nextItem();

                // Only write if logger is set to debug mode
                if (LOG.isDebugEnabled()) {

                    position++;

                    try (final StringWriter writer = new StringWriter()) {
                        final XQuerySerializer xqs = new XQuerySerializer(context.getBroker(), props, writer);
                        xqs.serialize(next.toSequence());

                        // Write to log
                        LOG.debug("{} [{}] [{}]: {}", label, position, Type.getTypeName(next.getType()), writer.toString());

                    } catch (final IOException | SAXException e) {
                        throw new XPathException(this, e.getMessage());
                    }

                }

                // Add to result
                result.add(next);
            }
        }

        return result;
    }
}
