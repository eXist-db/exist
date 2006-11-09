package org.exist.backup;

import java.io.*;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry;

/**
 * Implementation of BackupWriter that writes to a zip file.
 */
public class ZipWriter implements BackupWriter {

    private String currentPath;
    private ZipOutputStream out;
    private StringWriter contents;

    public ZipWriter(String zipFile, String collection) throws IOException {
        File file = new File(zipFile);
        out = new ZipOutputStream(new FileOutputStream(file));
        currentPath = collection;
    }

    public Writer newContents() throws IOException {
        contents = new StringWriter();
        return contents;
    }

    public void closeContents() throws IOException {
        ZipEntry entry = new ZipEntry(currentPath + "/__contents__.xml");
        out.putNextEntry(entry);
        out.write(contents.toString().getBytes("UTF-8"));
        out.closeEntry();
    }

    public OutputStream newEntry(String name) throws IOException {
        ZipEntry entry = new ZipEntry(currentPath + '/' + name);
        out.putNextEntry(entry);
        return out;
    }

    public void closeEntry() throws IOException {
        out.closeEntry();
    }

    public void newCollection(String name) {
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
}