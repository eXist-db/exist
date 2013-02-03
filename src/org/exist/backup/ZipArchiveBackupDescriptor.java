/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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

import org.exist.repo.RepoBackup;
import org.exist.util.EXistInputSource;
import org.exist.util.ZipEntryInputSource;

import java.io.*;

import java.util.Enumeration;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;


public class ZipArchiveBackupDescriptor extends AbstractBackupDescriptor {
    
    protected ZipFile  archive;
    protected ZipEntry descriptor;
    protected String   base;

    public ZipArchiveBackupDescriptor(File fileArchive) throws ZipException, IOException, FileNotFoundException {
        archive    = new ZipFile(fileArchive);

        //is it full backup?
        base       = "db/";
        descriptor = archive.getEntry(base + BackupDescriptor.COLLECTION_DESCRIPTOR);

        if((descriptor == null) || descriptor.isDirectory()) {

            base = null;

            //looking for highest collection
            //TODO: better to put some information on top?
            ZipEntry item = null;
            Enumeration<? extends ZipEntry> zipEnum = archive.entries();

            while(zipEnum.hasMoreElements()) {
                item = zipEnum.nextElement();

                if(!item.isDirectory()) {

                    if(item.getName().endsWith( BackupDescriptor.COLLECTION_DESCRIPTOR)) {

                        if((base == null) || (base.length() > item.getName().length())) {
                            descriptor = item;
                            base = item.getName();
                        }
                    }
                }
            }

            if(base != null) {
                base = base.substring(0, base.length() - BackupDescriptor.COLLECTION_DESCRIPTOR.length());
            }
        }

        if(descriptor == null) {
            throw new FileNotFoundException("Archive " + fileArchive.getAbsolutePath() + " is not a valid eXist backup archive");
        }
    }


    private ZipArchiveBackupDescriptor(ZipFile archive, String base) throws FileNotFoundException {
        this.archive = archive;
        this.base    = base;
        descriptor   = archive.getEntry(base + BackupDescriptor.COLLECTION_DESCRIPTOR);

        if((descriptor == null) || descriptor.isDirectory()) {
            throw new FileNotFoundException(archive.getName() + " is a bit corrupted (" + base + " descriptor not found): not a valid eXist backup archive");
        }
    }

    @Override
    public BackupDescriptor getChildBackupDescriptor(String describedItem) {
        BackupDescriptor bd = null;

        try {
            bd = new ZipArchiveBackupDescriptor( archive, base + describedItem + "/" );
        } catch(FileNotFoundException fnfe) {
            // DoNothing(R)
        }

        return bd;
    }

    @Override
    public BackupDescriptor getBackupDescriptor(String describedItem) {
        if((describedItem.length() > 0) && (describedItem.charAt(0) == '/')) {
            describedItem = describedItem.substring(1);
        }

        if(!describedItem.endsWith("/")) {
            describedItem = describedItem + '/';
        }
        
        BackupDescriptor bd = null;

        try {
            bd = new ZipArchiveBackupDescriptor(archive, describedItem);
        } catch(FileNotFoundException e) {
            // DoNothing(R)
        }
        
        return bd;
    }


    @Override
    public EXistInputSource getInputSource() {
        return new ZipEntryInputSource(archive, descriptor);
    }

    @Override
    public EXistInputSource getInputSource(String describedItem) {
        ZipEntry ze = archive.getEntry(base + describedItem);
        EXistInputSource retval = null;

        if((ze != null) && !ze.isDirectory()) {
            retval = new ZipEntryInputSource(archive, ze);
        }

        return retval;
    }

    @Override
    public String getSymbolicPath() {
        return archive.getName() + "#" + descriptor.getName();
    }

    @Override
    public String getSymbolicPath(String describedItem, boolean isChildDescriptor) {
        String retval = archive.getName() + "#" + base + describedItem;

        if(isChildDescriptor) {
            retval += "/" + BackupDescriptor.COLLECTION_DESCRIPTOR;
        }
        return retval;
    }

    @Override
    public Properties getProperties() throws IOException {
        Properties properties = null;
        ZipEntry ze = archive.getEntry(BACKUP_PROPERTIES);

        if(ze != null) {
            properties = new Properties();
            properties.load(archive.getInputStream( ze ));
        }
        return properties;
    }

    @Override
    public File getRepoBackup() throws IOException {
        ZipEntry ze = archive.getEntry(RepoBackup.REPO_ARCHIVE);

        if (ze == null)
            return null;
        File temp = File.createTempFile("expathrepo", "zip");
        FileOutputStream os = new FileOutputStream(temp);
        InputStream is = archive.getInputStream(ze);
        byte[] buf = new byte[4096];
        int count;
        while ((count = is.read(buf)) > 0) {
            os.write(buf, 0, count);
        }
        os.close();
        return temp;
    }

    @Override
    public File getParentDir() {
        return new File(archive.getName()).getParentFile();
    }

    @Override
    public String getName() {
        return new File(archive.getName()).getName();
    }
}
