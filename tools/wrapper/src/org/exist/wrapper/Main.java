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

import org.exist.start.Classpath;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

/**
 * Implementation of WrapperListener for Tanuki's Java Service Wrapper.
 * 
 * @author wolf
 */
public class Main implements WrapperListener {

	private Class klazz;
	private Object app;
	
	private Main() {
	}

	/**
	 * Start the included Jetty server using reflection. The ClassLoader is set up through eXist's
	 * bootstrap loader, so the wrapper doesn't need to know all jars.
	 * 
	 * @see org.tanukisoftware.wrapper.WrapperListener#start(java.lang.String[])
	 */
	public Integer start(String[] args) {
		System.setProperty("exist.register-shutdown-hook", "false");
		System.err.println("jetty.home = " + System.getProperty("jetty.home"));
		try {
			org.exist.start.Main loader = new org.exist.start.Main("jetty");
			File homeDir = loader.detectHome();
			Classpath classpath = loader.constructClasspath(homeDir, args);
			ClassLoader cl = classpath.getClassLoader(null);
			Thread.currentThread().setContextClassLoader(cl);
			
			klazz = cl.loadClass("org.exist.JettyStart");
			Class[] methodParamTypes = new Class[1];
			methodParamTypes[0] = args.getClass();
			Method method = klazz.getDeclaredMethod("run", methodParamTypes);
			
			app = klazz.newInstance();
			Object[] params = new Object[1];
			params[0] = args;
			method.invoke(app, params);
			
			return null;
		} catch (Exception e) {
			e.printStackTrace();
			WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_FATAL, 
					"An error occurred: " + e.getMessage());
		}
		return new Integer(1);
	}

	/* (non-Javadoc)
	 * @see org.tanukisoftware.wrapper.WrapperListener#stop(int)
	 */
	public int stop(int exitCode) {
		// wait up to 1 minute
		WrapperManager.signalStopping(60000);
		try {
			Method method = klazz.getDeclaredMethod("shutdown", new Class[0]);
			method.invoke(app, new Object[0]);
		} catch (Exception e) {
			WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_FATAL, 
					"Failed to shutdown the database: " + e.getMessage());
		}
		return exitCode;
	}

	/* (non-Javadoc)
	 * @see org.tanukisoftware.wrapper.WrapperListener#controlEvent(int)
	 */
	public void controlEvent(int event) {
		if(WrapperManager.isControlledByNativeWrapper()) {
			// the wrapper will take care of this event
		} else {
			if ((event == WrapperManager.WRAPPER_CTRL_C_EVENT) ||
					(event == WrapperManager.WRAPPER_CTRL_CLOSE_EVENT) ||
					(event == WrapperManager.WRAPPER_CTRL_SHUTDOWN_EVENT)) {
				WrapperManager.stop(0);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Main main = new Main();
		WrapperManager.start(main, args);
	}
}
