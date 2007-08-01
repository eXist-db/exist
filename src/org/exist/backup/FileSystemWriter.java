package org.exist.backup;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

/**
 * Implementation of BackupWriter that writes to the file system.
 */
public class FileSystemWriter implements BackupWriter {

    private File currentDir;
    private File currentContents;
    private Writer currentContentsOut;
    private OutputStream currentOut;

    public FileSystemWriter(String path) {
        File file = new File(path);
		if(file.exists()) {
			System.out.println("removing " + path);
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
        return currentContentsOut;
    }

    public void closeContents() throws IOException {
        currentContentsOut.close();
    }

    public OutputStream newEntry(String name) throws IOException {
        currentOut = new FileOutputStream(new File(currentDir, name));
        return currentOut;
    }

    public void closeEntry() throws IOException {
        currentOut.close();
    }
}
