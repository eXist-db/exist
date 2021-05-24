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
package org.exist.service;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.Main;
import org.exist.start.StartException;

public class ExistDbDaemon implements Daemon {

    private static final String MODE_JETTY = "jetty";

    private Main main = null;
    private String[] args = null;

    private void init(final String args[]) {
        this.main = new Main("jetty");
        this.args = args;
    }

    //<editor-fold desc="Jsvc Implementation">

    @Override
    public void init(final DaemonContext daemonContext) throws DaemonInitException {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        }

        if (this.main != null) {
            throw new DaemonInitException("Daemon already initialised");
        }
        init(daemonContext.getArguments());
    }

    @Override
    public void start() throws Exception {
        final String[] runArgs = new String[1 + args.length];
        runArgs[0] = MODE_JETTY;
        System.arraycopy(args, 0, runArgs, 1, args.length);

        this.main.runEx(runArgs);
    }

    @Override
    public void stop() throws Exception {
        this.main.shutdownEx();
    }

    @Override
    public void destroy() {
        this.args = null;
        this.main = null;
    }

    //</editor-fold>


    //<editor-fold desc="Procrun Implementation">

    private static ExistDbDaemon instance;

    static void start(final String[] args) throws Exception {
        if (instance != null) {
            throw new IllegalStateException("Instance already started");
        }

        instance = new ExistDbDaemon();
        instance.init(args);
        instance.start();
    }

    static void stop(final String[] args) throws Exception {
        if (instance == null) {
            throw new IllegalStateException("Instance already stopped");
        }

        instance.stop();
        instance.destroy();
        instance = null;
    }

    //</editor-fold>
}
