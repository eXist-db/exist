/*
Copyright (c) 2015, Adam Retter
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Stack;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.exist.util.FileUtils;

/**
 * Temporary File Manager
 *
 * Attempts to create and delete temporary files working around the issues of
 * some JDK platforms (e.g. Windows). Where deleting files is impossible, used
 * but finished with temporary files will be re-used where possible if they
 * cannot be deleted.
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class TemporaryFileManager {

    private final static Log LOG = LogFactory.getLog(TemporaryFileManager.class);

    private final static String FOLDER_PREFIX = "_mmtfm_";
    private final Stack<Path> available = new Stack<>();
    private final Path tmpFolder;

    private final static TemporaryFileManager instance = new TemporaryFileManager();

    public static TemporaryFileManager getInstance() {
        return instance;
    }

    private TemporaryFileManager() {
        cleanupOldTempFolders();

        try {
            this.tmpFolder = Files.createTempDirectory(FOLDER_PREFIX + UUID.randomUUID().toString());
        } catch(final IOException ioe) {
            throw new RuntimeException("Unable to create temporary folder", ioe);
        }

        //add hook to JVM to delete the file on exit
        //unfortunately this does not always work on all (e.g. Windows) platforms
        //will be recovered on restart by cleanupOldTempFolders
        tmpFolder.toFile().deleteOnExit();
        
        LOG.info("Temporary folder is: " + tmpFolder.toAbsolutePath().toString());
    }

    public final Path getTemporaryFile() throws IOException {

        Path tempFile = null;

        synchronized(available) {
            if(!available.empty()) {
                tempFile = available.pop();
            }
        }

        if(tempFile == null) {
            tempFile = Files.createTempFile(tmpFolder, "mmtf_" + System.currentTimeMillis(), ".tmp");

            //add hook to JVM to delete the file on exit
            //unfortunately this does not always work on all (e.g. Windows) platforms
            tempFile.toFile().deleteOnExit();
        }

        return tempFile;
    }

    public void returnTemporaryFile(final Path tempFile) {
        //Check if tempFile is still present ..
        if (Files.exists(tempFile)) {


            boolean deleted = false;
            try {
                deleted = Files.deleteIfExists(tempFile);
            } catch(final IOException e) {
                LOG.error("Unable to delete temporary file: " + tempFile.toAbsolutePath().toString(), e);
            }
            if(deleted) {
                LOG.debug("Deleted temporary file: " + tempFile.toAbsolutePath().toString());
            } else {
                LOG.debug("Could not delete temporary file: " + tempFile.toAbsolutePath().toString() + ". Returning to stack for re-use.");

                //if we couldnt delete it, add it to the stack of available files
                //for reuse in the future.
                //Typically there are problems deleting these files on Windows
                //platforms which is why this facility was added
                synchronized(available) {
                    //Check if tempFile is not allready present in stack ...
                    if (available.contains(tempFile)) {
                        LOG.debug("Temporary file: " + tempFile.toAbsolutePath().toString() + " already in stack. Skipping.");
                    } else {
                        available.push(tempFile);
                    }
                }
            }
        } else {
            LOG.debug("Trying to delete non existing file: " + tempFile.toAbsolutePath().toString());
        }
    }

    /**
     * Called at startup to attempt to cleanup
     * any left-over temporary folders
     * from the last time this was run
     */
    private void cleanupOldTempFolders() {
        final Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        try(final Stream<Path> tmpFiles = Files.list(tmpDir)) {
            tmpFiles
                    .filter(path -> Files.isDirectory(path) && path.startsWith(FOLDER_PREFIX))
                    .forEach(FileUtils::deleteQuietly);
        } catch(final IOException ioe) {
            LOG.error("Unable to delete old temporary folders", ioe);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {

            //remove references to available files
            available.clear();

            //try and remove our temporary folder
            FileUtils.deleteQuietly(tmpFolder);
        }
        finally {
            super.finalize();
        }
    }
}
