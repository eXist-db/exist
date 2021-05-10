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
package org.exist.backup;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.StartException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.SystemExitCodes;
import org.exist.xquery.TerminatedException;
import se.softhouse.jargo.Argument;
import se.softhouse.jargo.ArgumentException;
import se.softhouse.jargo.CommandLineParser;
import se.softhouse.jargo.ParsedArguments;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.exist.util.ArgumentUtil.getBool;
import static org.exist.util.ArgumentUtil.getOpt;
import static se.softhouse.jargo.Arguments.*;


public class ExportMain {

    /* general arguments */
    private static final Argument<?> helpArg = helpArgument("-h", "--help");
    private static final Argument<Boolean> verboseArg = optionArgument("-v", "--verbose")
            .description("print processed resources to stdout")
            .defaultValue(false)
            .build();

    /* control arguments */
    private static final Argument<Boolean> noCheckArg = optionArgument("-n", "--nocheck")
            .description("do not run a consistency check. Just export the data.")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> checkDocsArg = optionArgument("-s", "--check-docs")
            .description("scan every document to find errors in the the nodes stored (costs time)")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> directAccessArg = optionArgument("-D", "--direct")
            .description("use an (even more) direct access to the db, bypassing some index structures")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> exportArg = optionArgument("-x", "--export")
            .description("export database contents while preserving as much data as possible")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> noExportArg = optionArgument("--no-export")
            .description("do not export the database contents, overrides argument --export")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> incrementalArg = optionArgument("-i", "--incremental")
            .description("create incremental backup (use with --export|-x)")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> zipArg = optionArgument("-z", "--zip")
            .description("write output to a ZIP instead of a file system directory")
            .defaultValue(false)
            .build();
    private static final Argument<Boolean> noZipArg = optionArgument("--no-zip")
            .description("do not zip the output, overrides argument --zip")
            .defaultValue(false)
            .build();

    /* export parameters */
    private static final Argument<File> configArg = fileArgument("-c", "--config")
            .description("the database configuration (conf.xml) file to use for launching the db.")
            .build();
    private static final Argument<File> outputDirArg = fileArgument("-d", "--dir")
            .description("the directory to which all output will be written.")
            .defaultValue(Paths.get("export").toAbsolutePath().toFile())
            .build();

    protected static BrokerPool startDB(final Optional<Path> configFile) {
        try {
            final Configuration config;

            if (configFile.isPresent()) {
                config = new Configuration(configFile.get().toAbsolutePath().toString(), Optional.empty());
            } else {
                config = new Configuration();
            }
            config.setProperty(BrokerPool.PROPERTY_EXPORT_ONLY, Boolean.TRUE);
            BrokerPool.configure(1, 5, config);
            return (BrokerPool.getInstance());
        } catch (final DatabaseConfigurationException | EXistException e) {
            System.err.println("ERROR: Failed to open database: " + e.getMessage());
        }
        return (null);
    }


    @SuppressWarnings("unchecked")
    public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

            final ParsedArguments arguments = CommandLineParser
                    .withArguments(noCheckArg, checkDocsArg, directAccessArg, exportArg, noExportArg, incrementalArg, zipArg, noZipArg)
                    .andArguments(configArg, outputDirArg)
                    .andArguments(helpArg, verboseArg)
                    .parse(args);

            process(arguments);
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        } catch (final ArgumentException e) {
            System.out.println(e.getMessageAndUsage());
            System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);

        }
    }

    private static void process(final ParsedArguments arguments) {
        final boolean verbose = getBool(arguments, verboseArg);

        final boolean noCheck = getBool(arguments, noCheckArg);
        final boolean checkDocs = getBool(arguments, checkDocsArg);
        final boolean direct = getBool(arguments, directAccessArg);
        boolean export = getBool(arguments, exportArg);
        final boolean noExport = getBool(arguments, noExportArg);
        if (noExport) {
            export = false;
        }
        final boolean incremental = getBool(arguments, incrementalArg);
        boolean zip = getBool(arguments, zipArg);
        final boolean noZip = getBool(arguments, noZipArg);
        if (noZip) {
            zip = false;
        }

        final Optional<Path> dbConfig = getOpt(arguments, configArg).map(File::toPath);
        final Path exportTarget = arguments.get(outputDirArg).toPath();

        final BrokerPool pool = startDB(dbConfig);

        if (pool == null) {
            System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
        }
        int retval = 0; // return value

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            List<ErrorReport> errors = null;

            if (!noCheck) {
                final ConsistencyCheck checker = new ConsistencyCheck(broker, transaction, direct, checkDocs);
                errors = checker.checkAll(new CheckCallback());
            }

            if (errors != null && !errors.isEmpty()) {
                System.err.println("ERRORS FOUND.");
                retval = 1;
            } else {
                System.out.println("No errors.");
            }

            if (export) {
                if (!Files.exists(exportTarget)) {
                    Files.createDirectories(exportTarget);
                } else if(!Files.isDirectory(exportTarget)) {
                    System.err.println("Output dir already exists and is a file: " + exportTarget.toAbsolutePath().toString());
                    System.exit(SystemExitCodes.INVALID_ARGUMENT_EXIT_CODE);
                }
                final SystemExport sysexport = new SystemExport(broker, transaction, new Callback(verbose), null, direct);
                sysexport.export(exportTarget.toAbsolutePath().toString(), incremental, zip, errors);
            }

            transaction.commit();

        } catch (final EXistException e) {
            System.err.println("ERROR: Failed to retrieve database broker: " + e.getMessage());
            retval = SystemExitCodes.NO_BROKER_EXIT_CODE;
        } catch (final TerminatedException e) {
            System.err.println("WARN: Export was terminated by db.");
            retval = SystemExitCodes.TERMINATED_EARLY_EXIT_CODE;
        } catch (final PermissionDeniedException pde) {
            System.err.println("ERROR: Failed to retrieve database data: " + pde.getMessage());
            retval = SystemExitCodes.PERMISSION_DENIED_EXIT_CODE;
        } catch (final IOException ioe) {
            System.err.println("ERROR: Failed to retrieve database data: " + ioe.getMessage());
            retval = SystemExitCodes.IO_ERROR_EXIT_CODE;
        } finally {
            BrokerPool.stopAll(false);
        }
        System.exit(retval);
    }

    private static class Callback implements SystemExport.StatusCallback {

        private boolean verbose = false;

        public Callback(final boolean verbose) {
            this.verbose = verbose;
        }

        @Override
        public void startCollection(final String path) {
            if (verbose) {
                System.out.println("Entering collection " + path + " ...");
            }
        }

        @Override
        public void startDocument(final String name, final int count, final int docsCount) {
            if (verbose) {
                System.out.println("Writing document " + name + " [" + (count + 1) + " of " + docsCount + ']');
            }
        }

        @Override
        public void error(final String message, final Throwable exception) {
            System.err.println(message);

            if (exception != null) {
                exception.printStackTrace();
            }
        }
    }


    private static class CheckCallback implements org.exist.backup.ConsistencyCheck.ProgressCallback {
        @Override
        public void startDocument(final String name, final int current, final int count) {
        }

        @Override
        public void startCollection(final String path) {
        }

        @Override
        public void error(final ErrorReport error) {
            System.out.println(error.toString());
        }
    }
}
