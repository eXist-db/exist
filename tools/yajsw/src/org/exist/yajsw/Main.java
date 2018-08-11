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
public class Main implements Observer, Comparable {

    public static final int WAIT_HINT_UPDATE = 10000;

    public void start(String[] args) {
        try {
            // use the bootstrap loader to autodetect EXIST_HOME and
            // construct a correct classpath
            final org.exist.start.Main loader = new org.exist.start.Main(args[0]);
            final Path homeDir = loader.detectHome();
            final Classpath classpath = loader.constructClasspath(homeDir, args);
            final ClassLoader cl = classpath.getClassLoader(null);
            Thread.currentThread().setContextClassLoader(cl);

            final Class<?> klazz = cl.loadClass("org.exist.jetty.JettyStart");

            // find the run() method in the class
            final Class<?>[] methodParamTypes = new Class[2];
            methodParamTypes[0] = args.getClass();
            methodParamTypes[1] = Observer.class;
            final Method method = klazz.getDeclaredMethod("run", methodParamTypes);

            // create a new instance and invoke the run() method
            final Object app = klazz.newInstance();
            final String[] myArgs = new String[args.length - 1];
            for (int i = 1; i < args.length; i++)
                myArgs[i - 1] = args[i];
            final Object[] params = new Object[2];
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

    @Override
    public int compareTo(Object o) {
        return o == this ? 0 : -1;
    }
}
