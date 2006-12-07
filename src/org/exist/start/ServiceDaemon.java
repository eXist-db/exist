package org.exist.start;

import java.io.File;
import java.lang.reflect.Method;

/**
 * An apache commons daemon class to start eXist.
 * @author R. Alexander Milowski
 */
public class ServiceDaemon {

    String [] args;
    Main existMain;

    public ServiceDaemon() {
    }

    protected void finalize() {
        System.err.println("ServiceDaemon: instance "+this.hashCode()+
                           " garbage collected");
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
           File homeDir = existMain.detectHome();
           String [] noArgs = {};
           Classpath classpath = existMain.constructClasspath(homeDir,noArgs);
           ClassLoader cl = classpath.getClassLoader(null);
           Thread.currentThread().setContextClassLoader(cl);
           Class brokerPoolClass = cl.loadClass("org.exist.storage.BrokerPool");
           // This only works in Java 1.5
           //Method stopAll = brokerPoolClass.getDeclaredMethod("stopAll",java.lang.Boolean.TYPE);
           //stopAll.invoke(null,Boolean.TRUE);

           // This is the ugly Java 1.4 version
           Class [] paramTypes = new Class[1];
           paramTypes[0] = java.lang.Boolean.TYPE;
           Method stopAll = brokerPoolClass.getDeclaredMethod("stopAll",paramTypes);
           Object [] arguments = new Object[1];
           arguments[0] = Boolean.TRUE;
       
           stopAll.invoke(null,arguments);
        } catch (Exception ex) {
           ex.printStackTrace();
        }

        System.err.println("ServiceDaemon: stopped");
    }

    public void destroy() {
        System.err.println("ServiceDaemon: instance "+this.hashCode()+ " destroy");
    }

}
