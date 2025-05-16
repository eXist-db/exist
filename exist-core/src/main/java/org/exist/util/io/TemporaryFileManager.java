/*
 * Copyright (c) 2012, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Adam Retter Consulting nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.FileUtils;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.DELETE_ON_CLOSE;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Temporary File Manager.
 *
 * Provides temporary files for use by eXist-db and deals with cleaning them
 * up.
 *
 * Previously when returning a temporary file if it could not be deleted
 * (which often occurred on Microsoft Windows) we would add it to a queue
 * for reuse the next time a temporary file was required.
 *
 * On Microsoft Windows platforms this was shown to be unreliable. If the
 * temporary file had been Memory Mapped, there would be a lingering open file
 * handle which would only be closed when the GC reclaims the ByteBuffer
 * objects resulting from the mapping. This exhibited two problems:
 *     1. The previously memory mapped file could only be reused for further
 *         memory mapped I/O. Any traditional I/O or file system operations
 *         (e.g. copy, move, etc.) would result in a
 *         java.nio.file.FileSystemException.
 *     2. Keeping the previously memory mapped file in a queue, may result in
 *     strong indirect references to the ByteBuffer objects meaning that they
 *     will never be subject to GC, and therefore the file handles would never
 *     be released.
 * As such, we now never recycle temporary file objects. Instead we rely on the
 * GC to eventually close the file handles of any previously memory mapped files
 * and the Operating System to manage it's temporary file space.
 *
 * Relevant articles on the above described problems are:
 *     1. https://bugs.java.com/view_bug.do?bug_id=4715154
 *     2. https://bugs.openjdk.java.net/browse/JDK-8028683
 *     3. https://bugs.java.com/view_bug.do?bug_id=4724038
 *
 * @version 2.0
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class TemporaryFileManager {

    private final static Logger LOG = LogManager.getLogger(TemporaryFileManager.class);

    private static final String FOLDER_PREFIX = "exist-db-temp-file-manager";
    private static final String FILE_PREFIX = "exist-db-temp";
    private static final String LOCK_FILENAME = FOLDER_PREFIX + ".lck";
    private final Path tmpFolder;
    private final FileChannel lockChannel;

    private static final TemporaryFileManager instance = new TemporaryFileManager();

    public static TemporaryFileManager getInstance() {
        return instance;
    }

    private TemporaryFileManager() {
        cleanupOldTempFolders();
        try {
            this.tmpFolder = Files.createTempDirectory(FOLDER_PREFIX + '-');
            this.lockChannel = FileChannel.open(tmpFolder.resolve(LOCK_FILENAME), CREATE_NEW, WRITE, DELETE_ON_CLOSE);
            lockChannel.lock();
            Runtime.getRuntime().addShutdownHook(new Thread(this::cleanUpTempFolders));
        } catch(final IOException ioe) {
            throw new RuntimeException("Unable to create temporary folder", ioe);
        }
        LOG.info("Temporary folder is: {}", tmpFolder);
    }

    private void cleanUpTempFolders() {
        LOG.info("Removing temporary folder is: {}", tmpFolder);
        try {
            lockChannel.close();  // will release the lock on the lock file, and the lock file should be deleted
            //try and remove our temporary folder
            FileUtils.deleteQuietly(tmpFolder);
        } catch(final IOException ioe) {
            LOG.error("Feiled to cleanup {}", tmpFolder, ioe);
        }
    }

    public final Path getTemporaryFile() throws IOException {

        // Be sure that the temp directory exists, create otherwise. #3826
        if (!Files.exists(tmpFolder)) {
            LOG.debug("Recreating {}", tmpFolder);
            Files.createDirectories(tmpFolder);
        }

        final Path tempFile = Files.createTempFile(tmpFolder, FILE_PREFIX + '-', ".tmp");

        /*
        add hook to JVM to delete the file on exit
        unfortunately this does not always work on all (e.g. Windows) platforms
         */
        tempFile.toFile().deleteOnExit();

        return tempFile;
    }

    public void returnTemporaryFile(final Path tempFile) {
        try {
            if (Files.deleteIfExists(tempFile)) {
                LOG.debug("Deleted temporary file: {}", tempFile);
            }
        } catch (final IOException e) {
            // this can often occur on Microsoft Windows (especially if the file was memory mapped!) :-/
            LOG.warn("Unable to delete temporary file: {} due to: {}", tempFile, e.getMessage());
        }
    }

    /**
     * Called at startup to attempt to cleanup
     * any left-over temporary folders
     * from the last time this was run
     */
    private void cleanupOldTempFolders() {
        final Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
        try {
            for (final Path dir : FileUtils.list(tmpDir, path -> Files.isDirectory(path) && path.startsWith(FOLDER_PREFIX))) {
                final Path lockPath = dir.resolve(LOCK_FILENAME);
                if (!Files.exists(lockPath)) {
                    // no lock file present, so not in use
                    FileUtils.deleteQuietly(dir);
                } else {
                    // there is a lock file present, we must determine if it is locked (by another eXist-db instance)
                    try (final FileChannel otherLockChannel = FileChannel.open(lockPath, WRITE)) {
                        if (otherLockChannel.tryLock() != null) {
                            // not locked... so we now have the lock
                            FileUtils.deleteQuietly(dir);
                        }
                    }
                    // will release the lock
                }
            }
        } catch (final IOException ioe) {
            LOG.warn("Unable to delete old temporary folders", ioe);
        }
    }
}
