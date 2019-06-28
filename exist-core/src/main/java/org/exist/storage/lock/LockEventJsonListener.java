/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import net.jcip.annotations.NotThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A lock event listener which formats events as JSON and writes them to a file
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class LockEventJsonListener implements LockTable.LockEventListener {

    private final static Logger LOG = LogManager.getLogger(LockEventJsonListener.class);

    private volatile boolean registered = false;

    private final Path jsonFile;
    private final boolean prettyPrint;

    private OutputStream os = null;
    private JsonGenerator jsonGenerator = null;


    public LockEventJsonListener(final Path jsonFile) {
        this(jsonFile, false);
    }

    public LockEventJsonListener(final Path jsonFile, final boolean prettyPrint) {
        this.jsonFile = jsonFile;
        this.prettyPrint = prettyPrint;
    }

    @Override
    public void registered() {
        this.registered = true;
        try {
            this.os = Files.newOutputStream(jsonFile,
                    StandardOpenOption.WRITE, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
            final JsonFactory jsonFactory = new JsonFactory();
            this.jsonGenerator = jsonFactory.createGenerator(os, JsonEncoding.UTF8);
            if(prettyPrint) {
                this.jsonGenerator.setPrettyPrinter(new DefaultPrettyPrinter());
            }

            this.jsonGenerator.writeStartObject();
            this.jsonGenerator.writeArrayFieldStart("lockEvents");
        } catch (final IOException e) {
            LOG.error(e);
        }
    }

    @Override
    public void unregistered() {
        try {
            if(jsonGenerator != null) {
                this.jsonGenerator.writeEndArray();
                this.jsonGenerator.writeEndObject();
                this.jsonGenerator.close();
                this.jsonGenerator = null;
            }
        } catch (final IOException e) {
            LOG.error(e);
        }

        try {
            if(os != null) {
                this.os.close();
                this.os = null;
            }
        } catch (final IOException e) {
            LOG.error(e);
        }

        this.registered = false;
    }

    public boolean isRegistered() {
        return registered;
    }

    @Override
    public void accept(final LockTable.LockEventType lockEventType, final long timestamp, final long groupId,
            final LockTable.Entry entry) {
        if(!registered) {
            return;
        }

        if(jsonGenerator != null) {
            try {
                jsonGenerator.writeStartObject();

                    // read count first to ensure memory visibility from volatile!
                    final int localCount = entry.count;

                    jsonGenerator.writeNumberField("timestamp", timestamp);
                    jsonGenerator.writeStringField("lockEventType", lockEventType.name());
                    jsonGenerator.writeNumberField("groupId", groupId);
                    jsonGenerator.writeStringField("id", entry.id);
                    jsonGenerator.writeStringField("thread", entry.owner);
                    if (entry.stackTraces != null) {
                        for (final StackTraceElement[] stackTrace : entry.stackTraces) {
                            stackTraceToJson(stackTrace);
                        }
                    }

                    jsonGenerator.writeObjectFieldStart("lock");
                        jsonGenerator.writeStringField("type", entry.lockType.name());
                        jsonGenerator.writeStringField("mode", entry.lockMode.name());
                        jsonGenerator.writeNumberField("holdCount", localCount);
                    jsonGenerator.writeEndObject();

                jsonGenerator.writeEndObject();
            } catch(final IOException e) {
                LOG.error(e);
            }
        }
    }

    private void stackTraceToJson(@Nullable final StackTraceElement[] stackTrace) throws IOException {
        jsonGenerator.writeArrayFieldStart("trace");

            if(stackTrace != null) {
                for(final StackTraceElement stackTraceElement : stackTrace) {
                    jsonGenerator.writeStartObject();
                        jsonGenerator.writeStringField("methodName", stackTraceElement.getMethodName());
                        jsonGenerator.writeStringField("className", stackTraceElement.getClassName());
                        jsonGenerator.writeNumberField("lineNumber",  stackTraceElement.getLineNumber());
                    jsonGenerator.writeEndObject();
                }
            }

        jsonGenerator.writeEndArray();
    }
}
