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
import java.util.List;
import java.util.Properties;

import org.exist.EXistException;
import org.exist.backup.ConsistencyCheck;
import org.exist.backup.ErrorReport;
import org.exist.backup.SystemExport;
import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.management.TaskStatus;
import org.exist.security.PermissionDeniedException;
import org.exist.util.Configuration;
import org.exist.xquery.TerminatedException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConsistencyCheckTask implements SystemTask {

    private String exportDir;
    private boolean createBackup = false;
    private boolean createZip = true;
    private boolean paused = false;
    private boolean incremental = false;
    private boolean incrementalCheck = false;
    private boolean checkDocs = false;
    private int maxInc = -1;

    private File lastExportedBackup = null;

    private ProcessMonitor.Monitor monitor = new ProcessMonitor.Monitor();
    
    public final static String OUTPUT_PROP_NAME = "output";
    public final static String ZIP_PROP_NAME = "zip";
    public final static String BACKUP_PROP_NAME = "backup";
    public final static String INCREMENTAL_PROP_NAME = "incremental";
    public final static String INCREMENTAL_CHECK_PROP_NAME = "incremental-check";
    public final static String MAX_PROP_NAME = "max";
    public final static String CHECK_DOCS_PROP_NAME = "check-documents";

    private final static LoggingCallback logCallback = new LoggingCallback();
    
    @Override
    public boolean afterCheckpoint() {
    	return false;
    }
    
    public void configure(Configuration config, Properties properties) throws EXistException {
        
        exportDir = properties.getProperty(OUTPUT_PROP_NAME, "export");
        File dir = new File(exportDir);
        if (!dir.isAbsolute()) {
            dir = new File((String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR), exportDir);
        }
        dir.mkdirs();
        exportDir = dir.getAbsolutePath();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using output directory " + exportDir);
        }

        final String backup = properties.getProperty(BACKUP_PROP_NAME, "no");
        createBackup = backup.equalsIgnoreCase("YES");

        final String zip = properties.getProperty(ZIP_PROP_NAME, "yes");
        createZip = zip.equalsIgnoreCase("YES");
        
        final String inc = properties.getProperty(INCREMENTAL_PROP_NAME, "no");
        incremental = inc.equalsIgnoreCase("YES");

        final String incCheck = properties.getProperty(INCREMENTAL_CHECK_PROP_NAME, "yes");
        incrementalCheck = incCheck.equalsIgnoreCase("YES");

        final String max = properties.getProperty(MAX_PROP_NAME, "5");
        try {
            maxInc = Integer.parseInt(max);
        } catch (final NumberFormatException e) {
            throw new EXistException("Parameter 'max' has to be an integer");
        }

        final String check = properties.getProperty(CHECK_DOCS_PROP_NAME, "no");
        checkDocs = check.equalsIgnoreCase("YES");
    }

    @Override
    public void execute(DBBroker broker) throws EXistException {
        final Agent agentInstance = AgentFactory.getInstance();
        final BrokerPool brokerPool = broker.getBrokerPool();
        final TaskStatus endStatus = new TaskStatus(TaskStatus.Status.STOPPED_OK);

        agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.Status.INIT));

        if (paused) {
            LOG.info("Consistency check is paused.");
            agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.Status.PAUSED));
            return;
        }

        brokerPool.getProcessMonitor().startJob(ProcessMonitor.ACTION_BACKUP, null, monitor);

        PrintWriter report = null;
        try {
            boolean doBackup = createBackup;
            // TODO: don't use the direct access feature for now. needs more testing
            List<ErrorReport> errors = null;
            if (!incremental || incrementalCheck) {
                
                LOG.info("Starting consistency check...");
                
                report = openLog();
                final CheckCallback cb = new CheckCallback(report);

                final ConsistencyCheck check = new ConsistencyCheck(broker, false, checkDocs);
                agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.Status.RUNNING_CHECK));
                errors = check.checkAll(cb);
                
                if (!errors.isEmpty()) {
                    endStatus.setStatus(TaskStatus.Status.STOPPED_ERROR);
                    endStatus.setReason(errors);
                   
                    LOG.error("Errors found: " + errors.size());

                    doBackup = true;

                    if (fatalErrorsFound(errors)) {
                        LOG.error("Fatal errors were found: pausing the consistency check task.");   
                        paused = true;
                    }
                }
                
                LOG.info("Finished consistency check");
            }

            if (doBackup) {
                LOG.info("Starting backup...");

                final SystemExport sysexport = new SystemExport(broker, logCallback, monitor, false);
                lastExportedBackup = sysexport.export(exportDir, incremental, maxInc, createZip, errors);
                agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.Status.RUNNING_BACKUP));

                if (lastExportedBackup != null) {
                    LOG.info("Created backup to file: " + lastExportedBackup.getAbsolutePath());
                }
                
                LOG.info("Finished backup");
            }

        } catch (final TerminatedException e) {
            throw new EXistException(e.getMessage(), e);
            
        } catch (final PermissionDeniedException e) {
            //TODO should maybe throw PermissionDeniedException instead!
            throw new EXistException(e.getMessage(), e);
            
        } finally {
            if (report != null) {
                report.close();
            }
            
            agentInstance.changeStatus(brokerPool, endStatus);
            brokerPool.getProcessMonitor().endJob();
        }
    }

    /**
     * Gets the last exported backup
     */
    public File getLastExportedBackup()
    {
        return lastExportedBackup;
    }

    private boolean fatalErrorsFound(List<ErrorReport> errors) {
        for (final ErrorReport error : errors) {
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
            final File file = SystemExport.getUniqueFile("report", ".log", exportDir);
            final OutputStream os = new BufferedOutputStream(new FileOutputStream(file));
            return new PrintWriter(new OutputStreamWriter(os, UTF_8));
        } catch (final FileNotFoundException e) {
            throw new EXistException("ERROR: failed to create report file in " + exportDir, e);
        }
    }

    private static class LoggingCallback implements SystemExport.StatusCallback {

		public void startCollection(String path) throws TerminatedException {
						
		}

		public void startDocument(String name, int current, int count)
				throws TerminatedException {			
		}

		public void error(String message, Throwable exception) {
			LOG.error(message, exception);			
		}
    	
    }
    
    private class CheckCallback implements ConsistencyCheck.ProgressCallback, SystemExport.StatusCallback {

        private PrintWriter log;
        private boolean errorFound = false;

        private CheckCallback(PrintWriter log) {
            this.log = log;
        }

//        public void startDocument(String path) {
//        }

        public void startDocument(String name, int current, int count) throws TerminatedException {
            if (!monitor.proceed()) {
                throw new TerminatedException("consistency check terminated");
            }
            if ((current % 1000 == 0) || (current == count)) {
                log.write("  DOCUMENT: ");
                log.write(Integer.valueOf(current).toString());
                log.write(" of ");
                log.write(Integer.valueOf(count).toString());
                log.write('\n');
                log.flush();
            }
        }

        public void startCollection(String path) throws TerminatedException {
            if (!monitor.proceed()) {
                throw new TerminatedException("consistency check terminated");
            }
            if (errorFound) {
                log.write("----------------------------------------------\n");
            }
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