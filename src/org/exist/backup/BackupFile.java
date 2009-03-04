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
import java.util.Date;
import java.text.ParseException;

public class BackupFile {

    private File file;
    private Date date;

    public BackupFile(File file, String dateTime) {
        this.file = file;
        try {
            date = BackupDirectory.DATE_FORMAT.parse(dateTime);
        } catch (ParseException e) {
        }
    }

    public boolean after(BackupFile other) {
       return date.after(other.date);
    }

    public boolean after(long time) {
        return date.getTime() > time;
    }

    public boolean before(BackupFile other) {
        return date.before(other.date);
    }

    public boolean before(long time) {
        return time > date.getTime();
    }

    public File getFile() {
        return file;
    }

    public Date getDate() {
        return date;
    }
}
