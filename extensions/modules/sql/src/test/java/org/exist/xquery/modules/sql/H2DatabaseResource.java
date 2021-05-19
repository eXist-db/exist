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
package org.exist.xquery.modules.sql;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.h2.tools.Server;
import org.junit.rules.ExternalResource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Embedded H2 Database JUnit Test Resource.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class H2DatabaseResource extends ExternalResource {

    private static final Logger LOG =  LogManager.getLogger(H2DatabaseResource.class);

    private static final String DEFAULT_URL = "jdbc:h2:mem:test-1";
    private static final String DEFAULT_USER = "sa";
    private static final String DEFAULT_PASSWORD = "sa";

    private final String url;
    private final String user;
    private final String password;
    private final Optional<Integer> tcpPort;
    private Connection rootConnection = null;
    private Server server = null;

    public H2DatabaseResource() {
        this(DEFAULT_URL, DEFAULT_USER, DEFAULT_PASSWORD);
    }

    public H2DatabaseResource(final String url, final String user, final String password) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.tcpPort = Optional.empty();
    }

    public H2DatabaseResource(final String url, final String user, final String password, final int tcpPort) {
        this.url = url;
        this.user = user;
        this.password = password;
        this.tcpPort = Optional.of(tcpPort);
    }

    @Override
    protected void before() throws SQLException {
        if (rootConnection == null) {
            org.h2.Driver.load();

            // Start the server if configured to do so
            if (this.tcpPort.isPresent()) {
                this.server = Server.createTcpServer(new String[] { "-tcpPort", Integer.toString(tcpPort.get()) });
                this.server.start();
            }

            this.rootConnection = DriverManager.getConnection(url, user, password);

            LOG.info("Started H2Database...");

        } else {
            throw new IllegalStateException("H2Database is already running");
        }

    }

    @Override
    protected void after() {
        if (rootConnection != null) {
            try {
                final Statement stat = rootConnection.createStatement();
                stat.execute("SHUTDOWN");
                stat.close();
            } catch (final Exception e) {
                LOG.error(e);
            }
            try {
                rootConnection.close();
                rootConnection = null;
            } catch (final Exception e) {
                LOG.error(e);
            }
            if (server != null) {
                server.stop();
                server = null;
            }

            LOG.info("Stopped H2Database.");

        } else {
            throw new IllegalStateException("H2Database already stopped");
        }
    }

    public Class getDriverClass() {
        return org.h2.Driver.class;
    }

    public String getUrl() {
        return url;
    }

    public String getUser() {
        return user;
    }

    public String getPassword() {
        return password;
    }

    public Optional<Integer> getTcpPort() {
        return tcpPort;
    }

    public Connection getEmbeddedConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
