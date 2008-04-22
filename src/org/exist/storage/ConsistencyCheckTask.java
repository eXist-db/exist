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
package org.exist.storage;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.ConsistencyCheck;
import org.exist.backup.ErrorReport;
import org.exist.backup.SystemExport;
import org.exist.management.AgentFactory;
import org.exist.util.Configuration;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

public class ConsistencyCheckTask implements SystemTask {

    private static final Logger LOG = Logger.getLogger(ConsistencyCheckTask.class);

    private String exportDir;
    private boolean createBackup = false;
    private boolean paused = false;
    
    public void configure(Configuration config, Properties properties) throws EXistException {
        exportDir = properties.getProperty("output", "export");
        File dir = new File(exportDir);
        if (!dir.isAbsolute())
            dir = new File((String)config.getProperty(BrokerPool.PROPERTY_DATA_DIR), exportDir);
        dir.mkdirs();
        exportDir = dir.getAbsolutePath();

        if (LOG.isDebugEnabled())
            LOG.debug("Using output directory " + exportDir);

        String backup = properties.getProperty("backup", "no");
        createBackup = backup.equalsIgnoreCase("YES");
    }

    public void execute(DBBroker broker) throws EXistException {
        if (paused) {
            if (LOG.isDebugEnabled())
                LOG.debug("Consistency check is paused.");
            return;
        }
        long start = System.currentTimeMillis();
        PrintWriter report = openLog();
        CheckCallback cb = new CheckCallback(report);
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("Starting consistency check...");
            boolean doBackup = createBackup;
            ConsistencyCheck check = new ConsistencyCheck(broker);
            List errors = check.checkAll(cb);
            if (!errors.isEmpty()) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Errors found: " + errors.size());
                doBackup = true;
                paused = true;
            }
            AgentFactory.getInstance().updateErrors(broker.getBrokerPool(), errors, start);
            if (doBackup) {
                File exportFile = SystemExport.getUniqueFile("data", ".zip", exportDir);
                if (LOG.isDebugEnabled())
                    LOG.debug("Creating emergency backup to file: " + exportFile.getAbsolutePath());
                SystemExport sysexport = new SystemExport(broker, null);
                sysexport.export(exportFile.getAbsolutePath(), errors);
            }
        } finally {
            report.close();
        }
    }

    private PrintWriter openLog() throws EXistException {
        try {
            File file = SystemExport.getUniqueFile("report", ".log", exportDir);
            OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
            return new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new EXistException("ERROR: failed to create report file in " + exportDir, e);
        } catch (FileNotFoundException e) {
            throw new EXistException("ERROR: failed to create report file in " + exportDir, e);
        }
    }

    private class CheckCallback implements ConsistencyCheck.ProgressCallback, SystemExport.StatusCallback {

        private PrintWriter log;
        private boolean errorFound = false;

        private CheckCallback(PrintWriter log) {
            this.log = log;
        }

        public void startDocument(String path) {
        }

        public void startDocument(String name, int current, int count) {
        }

        public void startCollection(String path) {
            if (errorFound)
                log.write("----------------------------------------------\n");
            errorFound = false;
            log.write("COLLECTION: ");
            log.write(path);
            log.write('\n');
        }

        public void error(ErrorReport error) {
            log.write("----------------------------------------------\n");
            log.write(error.toString());
            log.write('\n');
        }

        public void error(String message, Throwable exception) {
            log.write("----------------------------------------------\n");
            log.write("EXPORT ERROR: ");
            log.write(message);
            log.write('\n');
            exception.printStackTrace(log);
        }
    }
}
