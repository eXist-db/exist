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
package org.exist.protocolhandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;
import org.exist.storage.txn.Txn;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Startup Trigger to register eXists URL Stream Handler
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author Dannes Wessels
 */
public class URLStreamHandlerStartupTrigger implements StartupTrigger {

    private final static Logger LOG = LogManager.getLogger(URLStreamHandlerStartupTrigger.class);

    public final static String JAVA_PROTOCOL_HANDLER_PKGS="java.protocol.handler.pkgs";
    public final static String EXIST_PROTOCOL_HANDLER="org.exist.protocolhandler.protocols";

    /*
    eXist may be started and stopped multiple times within the same JVM,
    for example when running the test suite. This guard ensures that
    we only attempt the registration once per JVM session
    */
    private final static AtomicBoolean registered = new AtomicBoolean();

    @Override
    public void execute(final DBBroker sysBroker, final Txn transaction, final Map<String, List<? extends Object>> params) {
        String mode = null;
        if (params != null) {
            List<?> list = params.get("mode");
            if (list != null && list.size() == 1) {
                mode = list.getFirst().toString();
            }
        }

        registerStreamHandlerFactory(mode == null ? Mode.DISK : Mode.valueOf(mode.toUpperCase()));
    }

    private void registerStreamHandlerFactory(Mode mode) {
        if(registered.compareAndSet(false, true)) {
            try {
                URL.setURLStreamHandlerFactory(new eXistURLStreamHandlerFactory(mode));
                LOG.info("Successfully registered eXistURLStreamHandlerFactory.");
            } catch (final Error ex) {
                LOG.warn("The JVM already has a URLStreamHandlerFactory registered, skipping...");

                String currentSystemProperty = System.getProperty(JAVA_PROTOCOL_HANDLER_PKGS);

                if (currentSystemProperty == null) {
                    // Nothing setup yet
                    LOG.info("Setting " + JAVA_PROTOCOL_HANDLER_PKGS + " to "
                            + EXIST_PROTOCOL_HANDLER);
                    System.setProperty(JAVA_PROTOCOL_HANDLER_PKGS, EXIST_PROTOCOL_HANDLER);
                } else {
                    // java.protocol.handler.pkgs is already setup, preserving settings
                    if (!currentSystemProperty.contains(EXIST_PROTOCOL_HANDLER)) {
                        // eXist handler is not setup yet
                        currentSystemProperty = currentSystemProperty + "|" + EXIST_PROTOCOL_HANDLER;
                        LOG.info("Setting " + JAVA_PROTOCOL_HANDLER_PKGS + " to {}", currentSystemProperty);
                        System.setProperty(JAVA_PROTOCOL_HANDLER_PKGS, currentSystemProperty);
                    } else {
                        LOG.info("System property " + JAVA_PROTOCOL_HANDLER_PKGS + " has not been updated.");
                    }
                }
            }
        }
    }
}
