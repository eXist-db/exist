/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.protocolhandler;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.storage.StartupTrigger;

import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * Startup Trigger to register eXists URL Stream Handler
 *
 * @author Adam Retter <adam@exist-db.org>
 * @author Dannes Wessels
 */
public class URLStreamHandlerStartupTrigger implements StartupTrigger {

    private final static Logger LOG = Logger.getLogger(URLStreamHandlerStartupTrigger.class);

    public final static String JAVA_PROTOCOL_HANDLER_PKGS="java.protocol.handler.pkgs";
    public final static String EXIST_PROTOCOL_HANDLER="org.exist.protocolhandler.protocols";

    @Override
    public void execute(final DBBroker sysBroker, final Map<String, List<? extends Object>> params) {
        registerStreamHandlerFactory();
    }

    private void registerStreamHandlerFactory() {
        try {
            URL.setURLStreamHandlerFactory(new eXistURLStreamHandlerFactory());
            LOG.info("Successfully registered eXistURLStreamHandlerFactory.");
        } catch (final Error ex) {
            LOG.warn("The JVM has already an URLStreamHandlerFactory registered, skipping...");

            String currentSystemProperty = System.getProperty(JAVA_PROTOCOL_HANDLER_PKGS);

            if(currentSystemProperty == null) {
                // Nothing setup yet
                LOG.info("Setting " + JAVA_PROTOCOL_HANDLER_PKGS + " to "
                        + EXIST_PROTOCOL_HANDLER);
                System.setProperty( JAVA_PROTOCOL_HANDLER_PKGS, EXIST_PROTOCOL_HANDLER );
            } else {
                // java.protocol.handler.pkgs is already setup, preserving settings
                if(currentSystemProperty.indexOf(EXIST_PROTOCOL_HANDLER) == -1) {
                    // eXist handler is not setup yet
                    currentSystemProperty = currentSystemProperty + "|" + EXIST_PROTOCOL_HANDLER;
                    LOG.info("Setting " + JAVA_PROTOCOL_HANDLER_PKGS + " to " + currentSystemProperty);
                    System.setProperty(JAVA_PROTOCOL_HANDLER_PKGS, currentSystemProperty);
                } else {
                    LOG.info("System property " + JAVA_PROTOCOL_HANDLER_PKGS + " has not been updated.");
                }
            }
        }
    }
}
