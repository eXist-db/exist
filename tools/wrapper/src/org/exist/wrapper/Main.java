/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.wrapper;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Observer;
import java.util.Observable;

import org.exist.start.Classpath;

import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

/**
 * Implementation of WrapperListener for Tanuki's Java Service Wrapper.
 * 
 * @author wolf
 */
public class Main implements WrapperListener, Observer {
    
    public static final int WAIT_HINT_STOP = 60000;
    public static final int WAIT_HINT_UPDATE = 10000;

	private Class<?> klazz;
	private Object app;

	/**
     * Private constructor
     */
    private Main() {
        // NOP
	}

	/**
	 * Start the included Jetty server using reflection. The ClassLoader is set up through eXist's
	 * bootstrap loader, so the wrapper doesn't need to know all jars.
	 * 
	 * The first argument passed to this method determines the run mode. It should
	 * be either "jetty" or "standalone".
     * fixme!- 
	 * 
	 * @see org.tanukisoftware.wrapper.WrapperListener#start(java.lang.String[])
	 */
	public Integer start(String[] args) {
		System.setProperty("exist.register-shutdown-hook", "false");
        
		WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_INFO, String.format("jetty.home = %s", System.getProperty("jetty.home")));
        
		try {
			// use the bootstrap loader to autodetect EXIST_HOME and
			// construct a correct classpath
			org.exist.start.Main loader = new org.exist.start.Main(args[0]);
			File homeDir = loader.detectHome();
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
		
            // All is Okay
			return null;
            
		} catch (Exception e) {
			WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_FATAL, String.format("An error occurred: %s", e.getMessage()));
            e.printStackTrace();
		}
        
        // An error occurred
		return new Integer(1);
	}

	/* (non-Javadoc)
	 * @see org.tanukisoftware.wrapper.WrapperListener#stop(int)
	 */
	public int stop(int exitCode) {
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_DEBUG, String.format("Stop with exit code '%s'", exitCode));
        
		// wait up to 1 minute
		WrapperManager.signalStopping(WAIT_HINT_STOP);
		try {
			Method method = klazz.getDeclaredMethod("shutdown", new Class[0]);
			method.invoke(app, new Object[0]);
            
		} catch (Exception e) {
            // Log message, ignore
            WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_DEBUG, e.getMessage());
            
            // DW: should return code be changed in this case?
		}
		return exitCode;
	}

	/* (non-Javadoc)
	 * @see org.tanukisoftware.wrapper.WrapperListener#controlEvent(int)
	 */
	public void controlEvent(int event) {
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_DEBUG, String.format("Received control event '%s'", event));
         
		if (event == WrapperManager.WRAPPER_CTRL_HUP_EVENT) {
			try {
				Method method = klazz.getDeclaredMethod("systemInfo", new Class[0]);
				method.invoke(app, new Object[0]);
			} catch (Exception e) {
                WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_DEBUG, e.getMessage());
				e.printStackTrace();
			}
            
		} else if(WrapperManager.isControlledByNativeWrapper()) {
			// the wrapper will take care of this event
            
		} else {
            if ((event == WrapperManager.WRAPPER_CTRL_C_EVENT)
                    || (event == WrapperManager.WRAPPER_CTRL_CLOSE_EVENT)
                    || (event == WrapperManager.WRAPPER_CTRL_SHUTDOWN_EVENT)) {
				WrapperManager.stop(0);
			}
		}
	}

    /* (non-Javadoc)
	 * @see java.util.Observer#update(Observable, Object)
	 */
    public void update(Observable o, Object arg) {
        
        WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_DEBUG, String.format("Observer update with value '%s'", arg));
        
        if ("shutdown".equals(arg)) {
            WrapperManager.signalStopping(WAIT_HINT_UPDATE);
        } else
        	WrapperManager.signalStarting(WAIT_HINT_UPDATE);
    }

	public static void main(String[] args) throws Exception {
		Main main = new Main();
		WrapperManager.start(main, args);
	}
}
