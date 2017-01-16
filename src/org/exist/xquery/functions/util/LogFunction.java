/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2009 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.source.StringSource;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
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
    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName(FUNCTION_LOG, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to the current logger.",
                    new SequenceType[]{PRIORITY_PARAMETER, MESSAGE_PARAMETER},
                    new SequenceType(Type.ITEM, Cardinality.EMPTY)
            ),
            new FunctionSignature(
                    new QName(FUNCTION_LOG_SYSTEM_OUT, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to System.out.",
                    new SequenceType[]{MESSAGE_PARAMETER},
                    new SequenceType(Type.ITEM, Cardinality.EMPTY)
            ),
            new FunctionSignature(
                    new QName(FUNCTION_LOG_SYSTEM_ERR, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to System.err.",
                    new SequenceType[]{MESSAGE_PARAMETER},
                    new SequenceType(Type.ITEM, Cardinality.EMPTY)
            ),
            new FunctionSignature(
                    new QName(FUNCTION_LOGAPP, UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Logs the message to the named logger",
                    new SequenceType[]{PRIORITY_PARAMETER, LOGGER_NAME_PARAMETER, MESSAGE_PARAMETER},
                    new SequenceType(Type.ITEM, Cardinality.EMPTY)
            )
    };

    public LogFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

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
        if (getSource() != null && getSource().getKey() != null) {
            buf.append(" ");

            // Do not write content of query from String into log.
            if (getSource() instanceof StringSource) {
                buf.append(getSource().type());
            } else {
                buf.append(getSource().getKey());
            }

        }

        buf.append(") ");

        // Iterate over all input
        while (i.hasNext()) {
            final Item next = i.nextItem();
            if (Type.subTypeOf(next.getType(), Type.NODE)) {
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                try {
                    buf.append(serializer.serialize((NodeValue) next));
                } catch (final SAXException e) {
                    throw (new XPathException(this, "An exception occurred while serializing node to log: " + e.getMessage(), e));
                }
            } else {
                buf.append(next.getStringValue());
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