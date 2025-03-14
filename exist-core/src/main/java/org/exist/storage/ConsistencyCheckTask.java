/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.ConsistencyCheck;
import org.exist.backup.ErrorReport;
import org.exist.backup.SystemExport;
import org.exist.management.Agent;
import org.exist.management.AgentFactory;
import org.exist.management.TaskStatus;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xquery.Expression;
import org.exist.xquery.TerminatedException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConsistencyCheckTask implements SystemTask {

    private final static Logger LOG = LogManager.getLogger(ConsistencyCheckTask.class);

    private String exportDir;
    private boolean createBackup = false;
    private boolean createZip = true;
    private boolean paused = false;
    private boolean incremental = false;
    private boolean incrementalCheck = false;
    private boolean checkDocs = false;
    private int maxInc = -1;

    private Path lastExportedBackup = null;

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

    @Override
    public String getName() {
        return "Consistency Check Task";
    }

    @Override
    public void configure(final Configuration config, final Properties properties) throws EXistException {
        exportDir = properties.getProperty(OUTPUT_PROP_NAME, "export");
        Path dir = Paths.get(exportDir);
        if (!dir.isAbsolute()) {
            dir = ((Path) config.getProperty(BrokerPool.PROPERTY_DATA_DIR)).resolve(exportDir);
        }

        try {
            Files.createDirectories(dir);
        } catch(final IOException ioe) {
            throw new EXistException("Unable to create export directory: " + exportDir, ioe);
        }

        exportDir = dir.toAbsolutePath().toString();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using output directory {}", exportDir);
        }

        final String backup = properties.getProperty(BACKUP_PROP_NAME, "no");
        createBackup = "YES".equalsIgnoreCase(backup);

        final String zip = properties.getProperty(ZIP_PROP_NAME, "yes");
        createZip = "YES".equalsIgnoreCase(zip);
        
        final String inc = properties.getProperty(INCREMENTAL_PROP_NAME, "no");
        incremental = "YES".equalsIgnoreCase(inc);

        final String incCheck = properties.getProperty(INCREMENTAL_CHECK_PROP_NAME, "yes");
        incrementalCheck = "YES".equalsIgnoreCase(incCheck);

        final String max = properties.getProperty(MAX_PROP_NAME, "5");
        try {
            maxInc = Integer.parseInt(max);
        } catch (final NumberFormatException e) {
            throw new EXistException("Parameter 'max' has to be an integer");
        }

        final String check = properties.getProperty(CHECK_DOCS_PROP_NAME, "no");
        checkDocs = "YES".equalsIgnoreCase(check);
    }

    @Override
    public void execute(final DBBroker broker, final Txn transaction) throws EXistException {
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

                final ConsistencyCheck check = new ConsistencyCheck(broker, transaction, false, checkDocs);
                agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.Status.RUNNING_CHECK));
                errors = check.checkAll(cb);
                
                if (!errors.isEmpty()) {
                    endStatus.setStatus(TaskStatus.Status.STOPPED_ERROR);
                    endStatus.setReason(errors);

                    LOG.error("Errors found: {}", errors.size());

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

                final SystemExport sysexport = new SystemExport(broker, transaction, logCallback, monitor, false);
                lastExportedBackup = sysexport.export(exportDir, incremental, maxInc, createZip, errors);
                agentInstance.changeStatus(brokerPool, new TaskStatus(TaskStatus.Status.RUNNING_BACKUP));

                if (lastExportedBackup != null) {
                    LOG.info("Created backup to file: {}", lastExportedBackup.toAbsolutePath().toString());
                }
                
                LOG.info("Finished backup");
            }

        } catch (final TerminatedException | PermissionDeniedException e) {
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
     *
     * @return the last exported backup, or null
     */
    public Path getLastExportedBackup()
    {
        return lastExportedBackup;
    }

    private boolean fatalErrorsFound(final List<ErrorReport> errors) {
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
            final Path file = SystemExport.getUniqueFile("report", ".log", exportDir);
            return new PrintWriter(Files.newBufferedWriter(file, UTF_8));
        } catch (final IOException e) {
            throw new EXistException("ERROR: failed to create report file in " + exportDir, e);
        }
    }

    private static class LoggingCallback implements SystemExport.StatusCallback {

        @Override
		public void startCollection(final String path) throws TerminatedException {
						
		}

		@Override
		public void startDocument(final String name, final int current, final int count) throws TerminatedException {
		}

		@Override
		public void error(final String message, final Throwable exception) {
			LOG.error(message, exception);			
		}
    	
    }
    
    private class CheckCallback implements ConsistencyCheck.ProgressCallback, SystemExport.StatusCallback {
        private final PrintWriter log;
        private boolean errorFound = false;

        private CheckCallback(final PrintWriter log) {
            this.log = log;
        }

        @Override
        public void startDocument(final String name, final int current, final int count) throws TerminatedException {
            if (!monitor.proceed()) {
                throw new TerminatedException((Expression) null, "consistency check terminated");
            }
            if ((current % 1000 == 0) || (current == count)) {
                log.write("  DOCUMENT: ");
                log.write(Integer.toString(current));
                log.write(" of ");
                log.write(Integer.valueOf(count).toString());
                log.write('\n');
                log.flush();
            }
        }

        @Override
        public void startCollection(final String path) throws TerminatedException {
            if (!monitor.proceed()) {
                throw new TerminatedException((Expression) null, "consistency check terminated");
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

        @Override
        public void error(final ErrorReport error) {
            log.write("----------------------------------------------\n");
            log.write(error.toString());
            log.write('\n');
            log.flush();
        }

        @Override
        public void error(final String message, final Throwable exception) {
            log.write("----------------------------------------------\n");
            log.write("EXPORT ERROR: ");
            log.write(message);
            log.write('\n');
            exception.printStackTrace(log);
            log.flush();
        }
    } 
}
