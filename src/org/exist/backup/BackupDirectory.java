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
package org.exist.backup;

import java.io.File;
import java.io.FileFilter;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.text.DateFormat;

public class BackupDirectory {

    public final static String FILE_REGEX = "(full|inc)(\\d{8}-\\d{4}).*";

    public final static DateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmm");

    private File dir;

    private Matcher matcher;

    public BackupDirectory(String dirPath) {
        this(new File(dirPath));
    }

    public BackupDirectory(File directory) {
        this.dir = directory;
        Pattern pattern = Pattern.compile(FILE_REGEX);
        matcher = pattern.matcher("");
    }

    public File createBackup(boolean incremental, boolean zip) {
        int counter = 0;
        File file;
        do {
            StringBuffer buf = new StringBuffer();
            buf.append(incremental ? "inc" : "full");
            buf.append(DATE_FORMAT.format(new Date()));
            if (counter++ > 0)
                buf.append('_').append(counter);
            if (zip)
                buf.append(".zip");
            file = new File(dir, buf.toString());
        } while (file.exists());
        return file;
    }

    public BackupFile lastBackupFile() {
        File[] files = dir.listFiles(new FileFilter() {
            public boolean accept(File path) {
                return path.isFile();
            }
        });

        BackupFile newest = null;
        for (int i = 0; i < files.length; i++) {
            matcher.reset(files[i].getName());
            if (matcher.matches()) {
                String dateTime = matcher.group(2);
                BackupFile backup = new BackupFile(files[i], dateTime);
                if (newest == null || backup.after(newest)) {
                    newest = backup;
                }
            }
        }
        return newest;
    }

}
