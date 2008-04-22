/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.storage.repair;

import org.apache.avalon.excalibur.cli.CLArgsParser;
import org.apache.avalon.excalibur.cli.CLOption;
import org.apache.avalon.excalibur.cli.CLOptionDescriptor;
import org.apache.avalon.excalibur.cli.CLUtil;
import org.exist.EXistException;
import org.exist.backup.SystemExport;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;

import java.util.List;

public class Main {

    //  command-line options
	private final static int HELP_OPT = 'h';
    private final static int EXPORT_OPT = 'x';

    private final static CLOptionDescriptor OPTIONS[] = new CLOptionDescriptor[] {
        new CLOptionDescriptor( "help", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            HELP_OPT, "print help on command line options and exit." ),
        new CLOptionDescriptor( "export", CLOptionDescriptor.ARGUMENT_DISALLOWED,
            EXPORT_OPT, "export database contents" )
    };

    protected static BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (DatabaseConfigurationException e) {
            System.err.println("ERROR: Failed to open database: " + e.getMessage());
        } catch (EXistException e) {
            System.err.println("ERROR: Failed to open database: " + e.getMessage());
        }
        return null;
    }

    public static void main(String[] args) {
        CLArgsParser optParser = new CLArgsParser( args, OPTIONS );
        if(optParser.getErrorString() != null) {
            System.err.println( "ERROR: " + optParser.getErrorString());
            return;
        }
        boolean check = true;
        boolean export = false;
        String exportTarget = "export/";

        List opt = optParser.getArguments();
        int size = opt.size();
        CLOption option;
        for(int i = 0; i < size; i++) {
            option = (CLOption)opt.get(i);
            switch(option.getId()) {
                case HELP_OPT :
                    System.out.println("Usage: java " + Main.class.getName() + " [options]");
                    System.out.println(CLUtil.describeOptions(OPTIONS).toString());
                    break;
                case EXPORT_OPT :
                    export = true;
                    check = false;
                    break;
            }
        }

        BrokerPool pool = startDB();
        if (pool == null) {
            System.exit(1);
        }
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            if (check) {
                ConsistencyCheck checker = new ConsistencyCheck(broker);
                List errors = checker.checkDocuments(null);
                if (errors != null) {
                    for (int i = 0; i < errors.size(); i++) {
                        ErrorReport report = (ErrorReport) errors.get(i);
                        System.err.println(report.toString());
                    }
                }
            }
            if (export) {
                SystemExport sysexport = new SystemExport(broker, exportTarget, new Callback());
                sysexport.export(null);
            }
        } catch (EXistException e) {
            System.err.println("ERROR: Failed to retrieve database broker: " + e.getMessage());
        } finally {
            pool.release(broker);
            BrokerPool.stopAll(false);
        }
    }

    private static class Callback implements SystemExport.StatusCallback {
        public void startCollection(String path) {
            System.out.println("Entering collection " + path + " ...");
        }

        public void startDocument(String name, int count, int docsCount) {
            System.out.println("Writing document " + name + " [" + count + " of " + docsCount + ']');
        }

        public void error(String message, Throwable exception) {
            System.err.println(message);
            exception.printStackTrace();
        }
    }
}
