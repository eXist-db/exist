package org.exist.start;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * An apache commons daemon class to start eXist.
 * @author R. Alexander Milowski
 */
public class ServiceDaemon {

    String [] args;
    Main existMain;

    public ServiceDaemon() {
    }

    protected void finalize() throws Throwable {
        try {
            System.err.println("ServiceDaemon: instance " + this.hashCode() + " garbage collected");
        }
        finally {
            super.finalize();
        }
    }

    public void init(String[] arguments)
    {
        System.err.println("ServiceDaemon: instance "+this.hashCode()+ " init");

        this.args = arguments;
        this.existMain = Main.getMain();

        System.err.println("ServiceDaemon: init done ");

    }

    public void start() {
        /* Dump a message */
        System.err.println("ServiceDaemon: starting");
        existMain.run(args);
    }

    public void stop()
    {
        /* Dump a message */
        System.err.println("ServiceDaemon: stopping");
        try {
            final Path homeDir = existMain.detectHome();
            final String [] noArgs = {};
            final Classpath classpath = existMain.constructClasspath(homeDir, noArgs);
            final ClassLoader cl = classpath.getClassLoader(null);
            Thread.currentThread().setContextClassLoader(cl);
            final Class<?> brokerPoolClass = cl.loadClass("org.exist.storage.BrokerPools");

            final Method stopAll = brokerPoolClass.getDeclaredMethod("stopAll", boolean.class);
            stopAll.setAccessible(true);
            stopAll.invoke(null, true);
        } catch (final Exception ex) {
           ex.printStackTrace();
        }

        System.err.println("ServiceDaemon: stopped");
    }

    public void destroy() {
        System.err.println("ServiceDaemon: instance "+this.hashCode()+ " destroy");
    }

}
