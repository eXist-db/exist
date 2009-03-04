package org.exist.backup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * Implementation of BackupWriter that writes to the file system.
 */
public class FileSystemWriter implements BackupWriter {

    private File currentDir;
    private File currentContents;
    private Writer currentContentsOut;
    private OutputStream currentOut;
    private boolean dataWritten = false;

    public FileSystemWriter(String path) {
        this(new File(path));
    }

    public FileSystemWriter(File file) {
		if(file.exists()) {
			//removing "path"
			file.delete();
		}
		file.mkdirs();
        currentDir = file;
    }

    public void newCollection(String name) {
        File file = new File(currentDir, name);
        if(file.exists()) {
			file.delete();
		}
		file.mkdirs();
        dataWritten = true;
        currentDir = file;
    }

    public void closeCollection() {
        currentDir = currentDir.getParentFile();
    }

    public void close() throws IOException {
    }

    public Writer newContents() throws IOException {
        currentContents = new File(currentDir, "__contents__.xml");
        currentContentsOut =
			new BufferedWriter(
				new OutputStreamWriter(new FileOutputStream(currentContents), "UTF-8"));
        dataWritten = true;
        return currentContentsOut;
    }

    public void closeContents() throws IOException {
        currentContentsOut.close();
    }

    public OutputStream newEntry(String name) throws IOException {
        currentOut = new FileOutputStream(new File(currentDir, name));
        dataWritten = true;
        return currentOut;
    }

    public void closeEntry() throws IOException {
        currentOut.close();
    }

    public void setProperties(Properties properties) throws IOException {
        if (dataWritten)
            throw new IOException("Backup properties need to be set before any backup data is written");
        File propFile = new File(currentDir, "backup.properties");
        OutputStream os = new FileOutputStream(propFile);
        properties.store(os, "Backup properties");
        os.close();
    }
}
