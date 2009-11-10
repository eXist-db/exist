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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.ConsistencyCheck;
import org.exist.backup.ErrorReport;
import org.exist.backup.SystemExport;
import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.management.TaskStatus;
import org.exist.util.Configuration;

public class ConsistencyCheckTask implements SystemTask {

    private static final Logger LOG = Logger.getLogger(ConsistencyCheckTask.class);

    private String exportDir;
    private boolean createBackup = false;
    private boolean paused = false;
    private boolean incremental = false;
    private boolean incrementalCheck = false;
    private int maxInc = -1;

    private File lastExportedBackup = null;

    public final static String OUTPUT_PROP_NAME = "output";
    public final static String BACKUP_PROP_NAME = "backup";
    public final static String INCREMENTAL_PROP_NAME = "incremental";
    public final static String INCREMENTAL_CHECK_PROP_NAME = "incremental-check";
    public final static String MAX_PROP_NAME = "max";


    
    public void configure(Configuration config, Properties properties) throws EXistException {
        exportDir = properties.getProperty(OUTPUT_PROP_NAME, "export");
        File dir = new File(exportDir);
        if (!dir.isAbsolute())
            dir = new File((String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR), exportDir);
        dir.mkdirs();
        exportDir = dir.getAbsolutePath();

        if (LOG.isDebugEnabled())
            LOG.debug("Using output directory " + exportDir);

        String backup = properties.getProperty(BACKUP_PROP_NAME, "no");
        createBackup = backup.equalsIgnoreCase("YES");
        String inc = properties.getProperty(INCREMENTAL_PROP_NAME, "no");
        incremental = inc.equalsIgnoreCase("YES");
        String incCheck = properties.getProperty(INCREMENTAL_CHECK_PROP_NAME, "yes");
        incrementalCheck = incCheck.equalsIgnoreCase("YES");
        String max = properties.getProperty(MAX_PROP_NAME, "5");
        try {
            maxInc = Integer.parseInt(max);
        } catch (NumberFormatException e) {
            throw new EXistException("Parameter 'max' has to be an integer");
        }
    }

    public void execute(DBBroker broker) throws EXistException {
        final Agent agentInstance = AgentFactory.getInstance();
        final BrokerPool brokerPool = broker.getBrokerPool();
        TaskStatus endStatus = new TaskStatus(TaskStatus.STOPPED_OK);

        agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.INIT));

        if (paused) {
            if (LOG.isDebugEnabled())
                LOG.debug("Consistency check is paused.");
            agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.PAUSED));
            return;
        }
        long start = System.currentTimeMillis();
        PrintWriter report = null;
        try {
            boolean doBackup = createBackup;
            // TODO: don't use the direct access feature for now. needs more
            // testing
            List errors = null;
            if (!incremental || incrementalCheck) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Starting consistency check...");
                report = openLog();
                CheckCallback cb = new CheckCallback(report);

                ConsistencyCheck check = new ConsistencyCheck(broker, false);
                agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.RUNNING_CHECK));
                errors = check.checkAll(cb);
                if (!errors.isEmpty()) {
                    endStatus.setStatus(TaskStatus.STOPPED_ERROR);
                    endStatus.setReason(errors);

                    if (LOG.isDebugEnabled())
                        LOG.debug("Errors found: " + errors.size());
                    doBackup = true;
                    if (fatalErrorsFound(errors)) {
                        if (LOG.isDebugEnabled())
                            LOG.debug("Fatal errors were found: pausing the consistency check task.");
                        paused = true;
                    }
                }
            }
            if (doBackup) {
                if (LOG.isDebugEnabled())
                    LOG.debug("Starting backup...");
                SystemExport sysexport = new SystemExport(broker, null, false);
                lastExportedBackup = sysexport.export(exportDir, incremental, maxInc, true, errors);
                agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.RUNNING_BACKUP));
                if (LOG.isDebugEnabled())
                    LOG.debug("Created backup to file: " + lastExportedBackup.getAbsolutePath());
            }
        } finally {
            if (report != null)
                report.close();
            agentInstance.changeStatus(brokerPool, endStatus);
        }
    }

    /**
     * Gets the last exported backup
     */
    public File getLastExportedBackup()
    {
        return lastExportedBackup;
    }

    private boolean fatalErrorsFound(List errors) {
        for (int i = 0; i < errors.size(); i++) {
            ErrorReport error = (ErrorReport) errors.get(i);
            switch (error.getErrcode()) {
            // the following errors are considered fatal: export the db and
                // stop the task
            case ErrorReport.CHILD_COLLECTION:
            case ErrorReport.RESOURCE_ACCESS_FAILED:
                return true;
            }
        }
        // no fatal errors
        return false;
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
            if ((current % 1000 == 0) || (current == count)) {
                log.write("  DOCUMENT: ");
                log.write(Integer.valueOf(current).toString());
                log.write(" of ");
                log.write(Integer.valueOf(count).toString());
                log.write('\n');
                log.flush();
            }
        }

        public void startCollection(String path) {
            if (errorFound)
                log.write("----------------------------------------------\n");
            errorFound = false;
            log.write("COLLECTION: ");
            log.write(path);
            log.write('\n');
            log.flush();
        }

        public void error(ErrorReport error) {
            log.write("----------------------------------------------\n");
            log.write(error.toString());
            log.write('\n');
            log.flush();
        }

        public void error(String message, Throwable exception) {
            log.write("----------------------------------------------\n");
            log.write("EXPORT ERROR: ");
            log.write(message);
            log.write('\n');
            exception.printStackTrace(log);
            log.flush();
        }
    }
}
