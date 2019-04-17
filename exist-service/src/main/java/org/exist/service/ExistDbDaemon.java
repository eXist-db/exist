package org.exist.service;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;
import org.exist.start.Main;

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
