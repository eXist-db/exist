package org.exist.yajsw;

import org.exist.start.Classpath;
import org.rzo.yajsw.app.WrapperJVMMain;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Observable;
import java.util.Observer;

/**
 *
 */
public class Main implements Observer {

    public static final int WAIT_HINT_UPDATE = 10000;
    public static final int WAIT_HINT_STOP = 60000;

    private Class<?> klazz;
    private Object app;

    public Main() {
    }

    public void start(String[] args) {
        System.setProperty("exist.register-shutdown-hook", "true");
        try {
            // use the bootstrap loader to autodetect EXIST_HOME and
            // construct a correct classpath
            org.exist.start.Main loader = new org.exist.start.Main(args[0]);
            Path homeDir = loader.detectHome();
            Classpath classpath = loader.constructClasspath(homeDir, args);
            ClassLoader cl = classpath.getClassLoader(null);
            Thread.currentThread().setContextClassLoader(cl);

            klazz = cl.loadClass("org.exist.jetty.JettyStart");

            // find the run() method in the class
            Class<?>[] methodParamTypes = new Class[2];
            methodParamTypes[0] = args.getClass();
            methodParamTypes[1] = Observer.class;
            Method method = klazz.getDeclaredMethod("run", methodParamTypes);

            // create a new instance and invoke the run() method
            app = klazz.newInstance();
            String[] myArgs = new String[args.length - 1];
            for (int i = 1; i < args.length; i++)
                myArgs[i - 1] = args[i];
            Object[] params = new Object[2];
            params[0] = myArgs;
            params[1] = this;
            method.invoke(app, params);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void update(Observable o, Object arg) {
        if ("shutdown".equals(arg)) {
            WrapperJVMMain.WRAPPER_MANAGER.signalStopping(WAIT_HINT_UPDATE);
        } else if ("started".equals(arg)) {
            WrapperJVMMain.WRAPPER_MANAGER.reportServiceStartup();
        }
    }

    public static void main(String[] args) {
        final Main main = new Main();
        main.start(args);
    }
}
