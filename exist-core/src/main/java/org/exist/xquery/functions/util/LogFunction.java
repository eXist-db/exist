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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;


/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author Andrzej Taramina (andrzej@chaeron.com)
 */
public class LogFunction extends BasicFunction {

    protected static final FunctionParameterSequenceType PRIORITY_PARAMETER
            = new FunctionParameterSequenceType("priority", Type.STRING, Cardinality.EXACTLY_ONE, "The logging priority: 'error', 'warn', 'debug', 'info', 'trace'");
    protected static final FunctionParameterSequenceType LOGGER_NAME_PARAMETER
            = new FunctionParameterSequenceType("logger-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the logger, eg: my.app.log");
    protected static final FunctionParameterSequenceType MESSAGE_PARAMETER
            = new FunctionParameterSequenceType("message", Type.ITEM, Cardinality.ZERO_OR_MORE, "The message to log");

    protected static final Logger logger = LogManager.getLogger(LogFunction.class);

    public static final String FUNCTION_LOG = "log";
    public static final String FUNCTION_LOGAPP = "log-app";
    public static final String FUNCTION_LOG_SYSTEM_OUT = "log-system-out";
    public static final String FUNCTION_LOG_SYSTEM_ERR = "log-system-err";
    public final static FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName(FUNCTION_LOG, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to the current logger.",
                    new SequenceType[]{PRIORITY_PARAMETER, MESSAGE_PARAMETER},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            ),
            new FunctionSignature(
                    new QName(FUNCTION_LOG_SYSTEM_OUT, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to System.out.",
                    new SequenceType[]{MESSAGE_PARAMETER},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            ),
            new FunctionSignature(
                    new QName(FUNCTION_LOG_SYSTEM_ERR, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to System.err.",
                    new SequenceType[]{MESSAGE_PARAMETER},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            ),
            new FunctionSignature(
                    new QName(FUNCTION_LOGAPP, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to the named logger",
                    new SequenceType[]{PRIORITY_PARAMETER, LOGGER_NAME_PARAMETER, MESSAGE_PARAMETER},
                    new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
            )
    };

    public LogFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // Force adaptive serialization
        final Properties props = new Properties();
        props.setProperty(OutputKeys.METHOD, "adaptive");

        SequenceIterator i;

        final String calledAs = getName().getLocalPart();

        // Find query parameter with actual data
        switch (calledAs) {
            case FUNCTION_LOG:
                i = args[1].unorderedIterator();
                if (args[1].isEmpty()) {
                    return (Sequence.EMPTY_SEQUENCE);
                }
                break;

            case FUNCTION_LOGAPP:
                i = args[2].unorderedIterator();
                if (args[2].isEmpty()) {
                    return (Sequence.EMPTY_SEQUENCE);
                }
                break;

            default:
                i = args[0].unorderedIterator();
                if (args[0].isEmpty()) {
                    return (Sequence.EMPTY_SEQUENCE);
                }
                break;
        }

        // Add line of the log statement
        final StringBuilder buf = new StringBuilder();
        buf.append("(");

        buf.append("Line: ");
        buf.append(this.getLine());

        // Add source information to the log statement when provided
        if (getSource() != null) {
            buf.append(' ').append(getSource().pathOrShortIdentifier());
        }

        buf.append(") ");

        // Iterate over all input
        while (i.hasNext()) {
            final Item next = i.nextItem();

            if(next instanceof StringValue){
                // Use simple string value
                buf.append(next.getStringValue());

            } else {
                // Serialize data
                try (StringWriter writer = new StringWriter()) {
                    XQuerySerializer xqs = new XQuerySerializer(context.getBroker(), props, writer);
                    xqs.serialize(next.toSequence());
                    buf.append(writer);

                } catch (IOException | SAXException e) {
                    throw new XPathException(this, e.getMessage());
                }
            }

        }

        // Finally write the log
        switch (calledAs) {
            case FUNCTION_LOG:
                final String loglevel = args[0].getStringValue().toLowerCase();
                writeLog(buf, loglevel, logger);
                break;

            case FUNCTION_LOG_SYSTEM_OUT:
                System.out.println(buf);
                break;

            case FUNCTION_LOG_SYSTEM_ERR:
                System.err.println(buf);
                break;

            case FUNCTION_LOGAPP: {
                final String loglevelapp = args[0].getStringValue().toLowerCase();
                final String logname = args[1].getStringValue();

                // Use specific logger when provided
                final Logger logger = (logname == null || logname.isEmpty()) ? LOG : LogManager.getLogger(logname);

                writeLog(buf, loglevelapp, logger);
                break;
            }
        }

        return (Sequence.EMPTY_SEQUENCE);
    }

    /**
     * Write log message to logger with a priority.
     *
     * @param buffer   The log text
     * @param loglevel The priority of the log message
     * @param logger   The actual logger
     */
    private void writeLog(final StringBuilder buffer, final String loglevel, final Logger logger) {
        switch (loglevel) {
            case "error":
                logger.error(buffer);
                break;
            case "warn":
                logger.warn(buffer);
                break;
            case "info":
                logger.info(buffer);
                break;
            case "trace":
                logger.trace(buffer);
                break;
            default:
                logger.debug(buffer);
                break;
        }
    }
}