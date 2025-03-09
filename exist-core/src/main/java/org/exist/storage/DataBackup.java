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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Optional;
import java.util.Properties;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DataBackup implements SystemTask {

    private final static Logger LOG = LogManager.getLogger(DataBackup.class);

    public static final String DATE_FORMAT_PICTURE = "yyyyMMddHHmmssS";
    private final SimpleDateFormat creationDateFormat = new SimpleDateFormat(DATE_FORMAT_PICTURE);

	private Path dest;
	private Optional<Path> lastBackup = Optional.empty();

    public DataBackup() {
    }

    public DataBackup(final Path destination) {
        dest = destination;
    }

    @Override
    public boolean afterCheckpoint() {
    	return true;
    }

    @Override
    public String getName() {
        return "Data Backup Task";
    }

    @Override
    public void configure(final Configuration config, final Properties properties) throws EXistException {
        dest = Paths.get(properties.getProperty("output-dir", "backup"));
        if (!dest.isAbsolute()) {
            dest = ((Path)config.getProperty(BrokerPool.PROPERTY_DATA_DIR)).resolve(dest);
        }
        if (Files.exists(dest) && !(Files.isWritable(dest) && Files.isDirectory(dest))) {
            throw new EXistException("Cannot write backup files to " + dest.toAbsolutePath() +
                    ". It should be a writable directory.");
        } else {
            try {
                Files.createDirectories(dest);
            } catch(final IOException ioe) {
                throw new EXistException("Unable to create directory: " + dest.toAbsolutePath(), ioe);
            }
        }

        LOG.debug("Setting backup data directory: {}", dest);
    }

    @Override
	public void execute(final DBBroker broker, final Txn transaction) throws EXistException {
		if (!(broker instanceof NativeBroker)) {
            throw new EXistException("DataBackup system task can only be used with the native storage backend");
		}
		
		LOG.debug("Backing up data files ...");
		
		final String creationDate = creationDateFormat.format(Calendar.getInstance().getTime());
        final Path outFilename = dest.resolve(creationDate + ".zip");
        this.lastBackup = Optional.of(outFilename);
        
        // Create the ZIP file
        LOG.debug("Archiving data files into: {}", outFilename);
        
        try(final ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(outFilename)))) {
            out.setLevel(Deflater.NO_COMPRESSION);
            final Callback cb = new Callback(out);
            broker.backupToArchive(cb);
            // close the zip file
		} catch (final IOException e) {
            LOG.error("An IO error occurred while backing up data files: {}", e.getMessage(), e);
		}
	}

    public Optional<Path> getLastBackup() {
        return lastBackup;
    }

    private static class Callback implements RawDataBackup {
        final private ZipOutputStream zout;

        private Callback(final ZipOutputStream out) {
            zout = out;
        }

        @Override
        public OutputStream newEntry(final String name) throws IOException {
            zout.putNextEntry(new ZipEntry(name));
            return zout;
        }

        @Override
        public void closeEntry() throws IOException {
            zout.closeEntry();
        }
    }
}


