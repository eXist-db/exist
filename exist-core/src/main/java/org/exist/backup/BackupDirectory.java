/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  $Id$
 */
package org.exist.backup;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class BackupDirectory {
    public final static Logger LOG = LogManager.getLogger(BackupDirectory.class);


    public final static String PREFIX_FULL_BACKUP_FILE = "full";
    public final static String PREFIX_INC_BACKUP_FILE = "inc";

    public final static String FILE_REGEX = "(" + PREFIX_FULL_BACKUP_FILE + "|" + PREFIX_INC_BACKUP_FILE + ")(\\d{8}-\\d{4}).*";

    public final static String DATE_FORMAT_PICTURE = "yyyyMMdd-HHmm";
    private final DateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT_PICTURE);


    private final Path dir;

    private final Matcher matcher;

    public BackupDirectory(final String dirPath) {
        this(Paths.get(dirPath));
    }


    public BackupDirectory(final Path directory) {
        this.dir = directory;
        final Pattern pattern = Pattern.compile(FILE_REGEX);
        matcher = pattern.matcher("");
    }

    public Path createBackup(final boolean incremental, final boolean zip) {
        int counter = 0;
        Path file;

        do {
            final StringBuilder buf = new StringBuilder();
            buf.append(incremental ? PREFIX_INC_BACKUP_FILE : PREFIX_FULL_BACKUP_FILE);
            buf.append(dateFormat.format(new Date()));

            if (counter++ > 0) {
                buf.append('_').append(counter);
            }

            if (zip) {
                buf.append(".zip");
            }
            file = dir.resolve(buf.toString());
        } while (Files.exists(file));
        return (file);
    }


    public BackupDescriptor lastBackupFile() throws IOException {
        final List<Path> files = FileUtils.list(dir);

        Path newest = null;
        Date newestDate = null;

        for (final Path file : files) {
            matcher.reset(FileUtils.fileName(file));

            if (matcher.matches()) {
                final String dateTime = matcher.group(2);

                try {
                    final Date date = dateFormat.parse(dateTime);

                    if ((newestDate == null) || date.after(newestDate)) {
                        newestDate = date;
                        newest = file;
                    }
                } catch (final ParseException e) {
                }
            }
        }
        BackupDescriptor descriptor = null;

        if (newest != null) {

            try {

                if (FileUtils.fileName(newest).toLowerCase().endsWith(".zip")) {
                    descriptor = new ZipArchiveBackupDescriptor(newest);
                } else {
                    descriptor = new FileSystemBackupDescriptor(newest, newest.resolve("db").resolve(BackupDescriptor.COLLECTION_DESCRIPTOR));
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
        return (descriptor);
    }

}
