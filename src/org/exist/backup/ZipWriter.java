package org.exist.backup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.Properties;

/**
 * Implementation of BackupWriter that writes to a zip file.
 */
public class ZipWriter implements BackupWriter {

    private String currentPath;
    private ZipOutputStream out;
    private StringWriter contents;
    private boolean dataWritten = false;

    public ZipWriter(String zipFile, String collection) throws IOException {
        this(new File(zipFile), collection);
    }

    public ZipWriter(File zipFile, String collection) throws IOException {
        out = new ZipOutputStream(new FileOutputStream(zipFile));
        currentPath = collection;
    }

    public Writer newContents() throws IOException {
        contents = new StringWriter();
        return contents;
    }

    public void closeContents() throws IOException {
        ZipEntry entry = new ZipEntry(mkRelative(currentPath) + "/__contents__.xml");
        out.putNextEntry(entry);
        out.write(contents.toString().getBytes("UTF-8"));
        out.closeEntry();
        dataWritten = true;
    }

    public OutputStream newEntry(String name) throws IOException {
        ZipEntry entry = new ZipEntry(mkRelative(currentPath) + '/' + name);
        out.putNextEntry(entry);
        dataWritten = true;
        return out;
    }

    public void closeEntry() throws IOException {
        out.closeEntry();
    }

    public void newCollection(String name) {
        if (name.startsWith("/"))
            currentPath = name;
        else
            currentPath = currentPath + '/' + name;
    }

    public void closeCollection() {
        int p = currentPath.lastIndexOf('/');
        if (p > 0)
            currentPath = currentPath.substring(0, p);
    }

    public void close() throws IOException {
        out.close();
    }

    public void setProperties(Properties properties) throws IOException {
        if (dataWritten)
            throw new IOException("Backup properties need to be set before any backup data is written");
        ZipEntry entry = new ZipEntry("backup.properties");
        out.putNextEntry(entry);
        properties.store(out, "Backup properties");
        out.closeEntry();
    }

    private String mkRelative(String path) {
    	if (path.length() > 0 && path.charAt(0) == '/')
    		return path.substring(1);
    	return path;
    }
}