/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Stack;
import java.util.UUID;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Temporary File Manager
 * 
 * Attempts to create and delete temporary files
 * working around the issues of some JDK platforms
 * (e.g. Windows). Where deleting files is impossible,
 * used but finished with temporary files will be re-used
 * where possible if they cannot be deleted.
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class TemporaryFileManager {
    
    private final static Log LOG = LogFactory.getLog(TemporaryFileManager.class);
    
    private final static String FOLDER_PREFIX = "_mmtfm_";
    private final Stack<File> available = new Stack<File>();
    private final File tmpFolder;
    
    private final static TemporaryFileManager instance = new TemporaryFileManager();
    
    public static TemporaryFileManager getInstance() {
        return instance;
    }
    
    private TemporaryFileManager() {
        final String tmpDir = System.getProperty("java.io.tmpdir");
        final File t = new File(tmpDir);
        
        cleanupOldTempFolders(t);
        
        this.tmpFolder = new File(t, FOLDER_PREFIX + UUID.randomUUID().toString());
        if(!tmpFolder.mkdir()) {
            throw new RuntimeException("Unable to use temporary folder: " +  tmpFolder.getAbsolutePath());
        }
        
        LOG.info("Temporary folder is: " + tmpFolder.getAbsolutePath());
    }
    
    public final File getTemporaryFile() throws IOException {
        
        File tempFile = null;
        
        synchronized(available) {
            if(!available.empty()) {
                tempFile = available.pop();
            }
        }
        
        if(tempFile == null) {
            tempFile = File.createTempFile("mmtf_" + System.currentTimeMillis(), ".tmp", tmpFolder);
        
            //add hook to JVM to delete the file on exit
            //unfortunately this does not always work on all (e.g. Windows) platforms
            tempFile.deleteOnExit();
        }
        
        return tempFile;
    }
    
    public void returnTemporaryFile(final File tempFile) {
        
        //attempt to delete the temporary file
        final boolean deleted = tempFile.delete();
        
        if(deleted) {
            LOG.debug("Deleted temporary file: " + tempFile.getAbsolutePath());
        } else {
            LOG.debug("Could not delete temporary file: " + tempFile.getAbsolutePath() + ". Returning to stack for re-use.");
            
            //if we couldnt delete it, add it to the stack of available files
            //for reuse in the future.
            //Typically there are problems deleting these files on Windows
            //platforms which is why this facility was added
            synchronized(available) {
                available.push(tempFile);
            }
        }
    }
    
    private void cleanupOldTempFolders(final File t) {
        final File oldFolders[] = t.listFiles(new FileFilter(){
            @Override
            public boolean accept(File f) {
                return f.isDirectory() && f.getName().startsWith(FOLDER_PREFIX);
            }
        });
        
        for(final File oldFolder : oldFolders) {
            deleteFolder(oldFolder);
        }
    }
    
    private void deleteFolder(final File folder) {
        try {
            FileUtils.deleteDirectory(folder);
            LOG.debug("Deleted temporary folder: " + folder.getAbsolutePath());
        } catch(final IOException ioe) {
            LOG.warn("Unable to delete temporary folder: " + folder.getAbsolutePath(), ioe);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        
        //remove references to available files
        available.clear();
        
        //try and remove our temporary folder
        deleteFolder(tmpFolder);
    }
}