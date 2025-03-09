/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.storage.lock;

import java.io.Writer;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.lock.LockTable.LockCountTraces;
import org.exist.storage.lock.LockTable.LockModeOwner;
import org.exist.xquery.value.TimeUtils;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * Utilities for working with the Lock Table
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LockTableUtils {

    private static final String EOL = System.lineSeparator();

    public static String stateToString(final LockTable lockTable, final boolean includeStack) {
        final Map<String, Map<LockType, List<LockModeOwner>>> attempting = lockTable.getAttempting();
        final Map<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> acquired = lockTable.getAcquired();

        final StringBuilder builder = new StringBuilder();

        builder
                .append(EOL)
                .append("Acquired Locks").append(EOL)
                .append("------------------------------------").append(EOL);

        for(final Map.Entry<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> acquire : acquired.entrySet()) {
            builder.append(acquire.getKey()).append(EOL);
            for(final Map.Entry<LockType, Map<LockMode, Map<String, LockCountTraces>>> type : acquire.getValue().entrySet()) {
                builder.append('\t').append(type.getKey()).append(EOL);
                for(final Map.Entry<LockMode, Map<String, LockCountTraces>> lockModeOwners : type.getValue().entrySet()) {
                    builder
                            .append("\t\t").append(lockModeOwners.getKey())
                            .append('\t');

                    boolean firstOwner = true;
                    for(final Map.Entry<String, LockCountTraces> ownerHoldCount : lockModeOwners.getValue().entrySet()) {
                        if(!firstOwner) {
                            builder.append(", ");
                        } else {
                            firstOwner = false;
                        }
                        final LockCountTraces holdCount = ownerHoldCount.getValue();
                        builder.append(ownerHoldCount.getKey())
                                .append(" (count=").append(holdCount.count).append(")");
                        if (holdCount.traces != null && includeStack) {
                            for (int i = 0; i < holdCount.traces.size(); i++) {
                                 final StackTraceElement[] trace = holdCount.traces.get(i);
                                 builder
                                         .append(EOL)
                                         .append("\t\t\tTrace ").append(i).append(": ").append(EOL);
                                for (StackTraceElement stackTraceElement : trace) {
                                    builder.append("\t\t\t\t").append(stackTraceElement).append(EOL);
                                }
                            }
                        }
                    }
                    builder.append(EOL);
                }
            }
        }

        builder.append(EOL).append(EOL);

        builder
                .append("Attempting Locks").append(EOL)
                .append("------------------------------------").append(EOL);

        for(final Map.Entry<String, Map<Lock.LockType, List<LockTable.LockModeOwner>>> attempt : attempting.entrySet()) {
            builder.append(attempt.getKey()).append(EOL);
            for(final Map.Entry<Lock.LockType, List<LockTable.LockModeOwner>> type : attempt.getValue().entrySet()) {
                builder.append('\t').append(type.getKey()).append(EOL);
                for(final LockTable.LockModeOwner lockModeOwner : type.getValue()) {
                    builder
                            .append("\t\t").append(lockModeOwner.getLockMode())
                            .append('\t').append(lockModeOwner.getOwnerThread());
                            if (lockModeOwner.trace != null && includeStack) {
                                builder.append(EOL).append("\t\t\tTrace ").append(": ").append(EOL);
                                for (int i = 0; i < lockModeOwner.trace.length; i++) {
                                    builder.append("\t\t\t\t").append(lockModeOwner.trace[i]).append(EOL);
                                }
                            }
                            builder.append(EOL);
                }
            }
        }

        return builder.toString();
    }

    public static void stateToXml(final LockTable lockTable, final boolean includeStack, final Writer writer) throws XMLStreamException {
        final GregorianCalendar cal = new GregorianCalendar();

        final Map<String, Map<LockType, List<LockModeOwner>>> attempting = lockTable.getAttempting();
        final Map<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> acquired = lockTable.getAcquired();

        final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
        final XMLStreamWriter xmlWriter = outputFactory.createXMLStreamWriter(writer);

        xmlWriter.writeStartDocument();
        xmlWriter.writeStartElement("lock-table");
        final XMLGregorianCalendar xmlCal = TimeUtils.getInstance().newXMLGregorianCalendar(cal);
        xmlWriter.writeAttribute("timestamp", xmlCal.toXMLFormat());

        // acquired locks
        xmlWriter.writeStartElement("acquired");
        for(final Map.Entry<String, Map<LockType, Map<LockMode, Map<String, LockCountTraces>>>> acquire : acquired.entrySet()) {
            xmlWriter.writeStartElement("lock");
            xmlWriter.writeAttribute("id", acquire.getKey());

            for(final Map.Entry<LockType, Map<LockMode, Map<String, LockCountTraces>>> type : acquire.getValue().entrySet()) {
                xmlWriter.writeStartElement("type");
                xmlWriter.writeAttribute("id", type.getKey().name());

                for(final Map.Entry<LockMode, Map<String, LockCountTraces>> lockModeOwners : type.getValue().entrySet()) {
                    xmlWriter.writeStartElement("mode");
                    xmlWriter.writeAttribute("id", lockModeOwners.getKey().name());

                    for(final Map.Entry<String, LockCountTraces> ownerHoldCount : lockModeOwners.getValue().entrySet()) {
                        xmlWriter.writeStartElement("thread");
                        xmlWriter.writeAttribute("id", ownerHoldCount.getKey());
                        final LockCountTraces holdCount = ownerHoldCount.getValue();
                        xmlWriter.writeAttribute("hold-count", Integer.toString(holdCount.count));

                        if (holdCount.traces != null && includeStack) {
                            for (int i = 0; i < holdCount.traces.size(); i++) {
                                xmlWriter.writeStartElement("stack-trace");
                                xmlWriter.writeAttribute("index", Integer.toString(i));

                                final StackTraceElement[] trace = holdCount.traces.get(i);
                                for (int j = 0; j < trace.length; j++) {
                                    xmlWriter.writeStartElement("call");
                                    final StackTraceElement call = trace[j];
                                    xmlWriter.writeAttribute("index", Integer.toString(j));
                                    xmlWriter.writeAttribute("class", call.getClassName());
                                    xmlWriter.writeAttribute("method", call.getMethodName());
                                    xmlWriter.writeAttribute("file", call.getFileName());
                                    xmlWriter.writeAttribute("line", Integer.toString(call.getLineNumber()));
                                    xmlWriter.writeCharacters(call.toString());
                                    xmlWriter.writeEndElement();
                                }

                                xmlWriter.writeEndElement();
                            }
                        }
                        xmlWriter.writeEndElement();
                    }
                    xmlWriter.writeEndElement();
                }
                xmlWriter.writeEndElement();
            }
            xmlWriter.writeEndElement();
        }
        xmlWriter.writeEndElement();


        // attempting locks
        xmlWriter.writeStartElement("attempting");
        for(final Map.Entry<String, Map<Lock.LockType, List<LockTable.LockModeOwner>>> attempt : attempting.entrySet()) {
            xmlWriter.writeStartElement("lock");
            xmlWriter.writeAttribute("id", attempt.getKey());

            for(final Map.Entry<Lock.LockType, List<LockTable.LockModeOwner>> type : attempt.getValue().entrySet()) {
                xmlWriter.writeStartElement("type");
                xmlWriter.writeAttribute("id", type.getKey().name());

                for(final LockTable.LockModeOwner lockModeOwner : type.getValue()) {
                    xmlWriter.writeStartElement("mode");
                    xmlWriter.writeAttribute("id", lockModeOwner.getLockMode().name());

                    xmlWriter.writeStartElement("thread");
                    xmlWriter.writeAttribute("id", lockModeOwner.getOwnerThread());

                    if (lockModeOwner.trace != null && includeStack) {
                        xmlWriter.writeStartElement("stack-trace");

                        for (int i = 0; i < lockModeOwner.trace.length; i++) {
                            xmlWriter.writeStartElement("call");
                            final StackTraceElement call = lockModeOwner.trace[i];
                            xmlWriter.writeAttribute("index", Integer.toString(i));
                            xmlWriter.writeAttribute("class", call.getClassName());
                            xmlWriter.writeAttribute("method", call.getMethodName());
                            xmlWriter.writeAttribute("file", call.getFileName());
                            xmlWriter.writeAttribute("line", Integer.toString(call.getLineNumber()));
                            xmlWriter.writeCharacters(call.toString());
                            xmlWriter.writeEndElement();
                        }

                        xmlWriter.writeEndElement();
                    }

                    xmlWriter.writeEndElement();

                    xmlWriter.writeEndElement();
                }

                xmlWriter.writeEndElement();
            }

            xmlWriter.writeEndElement();
        }

        xmlWriter.writeEndElement();

        xmlWriter.writeEndElement();
        xmlWriter.writeEndDocument();
    }
}
