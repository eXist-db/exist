/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.izpack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.Writer;

import com.izforge.izpack.installer.AutomatedInstaller;

/**
 * @author wolf
 *
 */
public class CommandLineInstaller {

	private final static int HELP_OPT = 'h';
	private final static int PATH_OPT = 'p';
	
	/**
	 * 
	 */
	public CommandLineInstaller() {
	}

	protected void execute(String[] args) throws Exception {
		if (args.length == 0 && !System.getProperty("os.name").startsWith("OpenServer")) {
			System.out.println("\neXist Installation");
			System.out.println("------------------------");
			System.out.println("Using GUI mode ...");
			System.out.println("On a headless system, add parameter -p install-path");
			System.out.println("to install without GUI. If you are running JDK 1.4, you may");
			System.out.println("also try to pass: -Djava.awt.headless=true -p install-path");
			System.out.println("as arguments to the Java executable.");
 			
 			// can't load the GUIInstaller class on headless machines,
 			// so we use Class.forName to force lazy loading.
 			Class.forName("com.izforge.izpack.installer.GUIInstaller").newInstance();
 		} else {
            String installPath = System.getProperty("user.home") + "/eXist";
            for(int i = 0; i < args.length; i++) {
                if (args[i].equals("-h")) {
                    printHelp();
                    return;
                } else if (args[i].equals("-p")) {
                    if (++i == args.length) {
                        System.out.println("Option -p requires an argument: the path to the directory " +
                                "where you want to have eXist installed.");
                        return;
                    }
                    installPath = args[i];
                }
            }
			System.out.println("Installing into directory: " + installPath);
            
            String filename = File.createTempFile("inst", ".xml").getAbsolutePath();
            Writer w = new FileWriter(filename);
            w.write("<AutomatedInstallation langpack=\"eng\">\n");
			w.write("<com.izforge.izpack.panels.HelloPanel/>\n" +
					"    <com.izforge.izpack.panels.PacksPanel>\n" +
					"        <selected>\n" +
					"            <pack index=\"0\"/>\n" +
					"            <pack index=\"1\"/>\n" +
					"            <pack index=\"2\"/>\n" +
					"        </selected>\n" +
					"    </com.izforge.izpack.panels.PacksPanel>\n");
			w.write("<com.izforge.izpack.panels.TargetPanel>\n" +
					"        <installpath>" + installPath + "</installpath>\n" +
					"    </com.izforge.izpack.panels.TargetPanel>\n");
			w.write("<com.izforge.izpack.panels.InstallPanel/>\n");
			w.write("<com.izforge.izpack.panels.FinishPanel/>\n");
			w.write("</AutomatedInstallation>");
			w.close();
			
			new AutomatedInstaller(filename);

            new File(filename).delete();
 		}
	}
	
	private static void printHelp() {
        System.out.println("Usage: java " + CommandLineInstaller.class.getName() + " [options]");
        System.out.println("Options:");
        System.out.println("    -p install-path");
        System.out.println("        Install eXist with default options and no GUI into directory 'install-path'");
        System.out.println("    -h");
        System.out.println("        Print this help message and exit");
    }
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		CommandLineInstaller inst = new CommandLineInstaller();
		try {
			inst.execute(args);
		} catch (Exception e) {
			System.err.println("Installer exited with an error: " + e.getMessage());
		}
	}

}
