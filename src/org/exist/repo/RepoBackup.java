package org.exist.repo;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.FileUtils;
import org.exist.xmldb.XmldbURI;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility methods for backing up and restoring the expath file system repository.
 */
public class RepoBackup {

    public final static String REPO_ARCHIVE = "expathrepo.zip";

    public static Path backup(final DBBroker broker) throws IOException {
        final Path tempFile = Files.createTempFile("expathrepo", "zip");
        try(final ZipOutputStream os = new ZipOutputStream(Files.newOutputStream(tempFile))) {
            final Path directory = ExistRepository.getRepositoryDir(broker.getConfiguration());
            zipDir(directory.toAbsolutePath(), os, "");
        }
        return tempFile;
    }

    public static void restore(final DBBroker broker) throws IOException, PermissionDeniedException {
        final XmldbURI docPath = XmldbURI.createInternal(XmldbURI.ROOT_COLLECTION + "/" + REPO_ARCHIVE);
        DocumentImpl doc = null;
        try {
            doc = broker.getXMLResource(docPath, Lock.READ_LOCK);
            if (doc == null)
                {return;}
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                {throw new IOException(docPath + " is not a binary resource");}

            final Path file = ((NativeBroker)broker).getCollectionBinaryFileFsPath(doc.getURI());
            final Path directory = ExistRepository.getRepositoryDir(broker.getConfiguration());
            unzip(file, directory);
        } finally {
            if (doc != null)
                {doc.getUpdateLock().release(Lock.READ_LOCK);}
        }
    }

    /**
     * Zip up a directory path
     *
     * @param directory
     * @param zos
     * @param path
     * @throws IOException
     */
    public static void zipDir(final Path directory, final ZipOutputStream zos, final String path) throws IOException {
        // get a listing of the directory content
        final List<Path> dirList = FileUtils.list(directory);

        // loop through dirList, and zip the files
        for (final Path f : dirList) {
            if (Files.isDirectory(f)) {
                zipDir(f, zos, path + FileUtils.fileName(f) + "/");
                continue;
            }

            final ZipEntry anEntry = new ZipEntry(path + FileUtils.fileName(f));
            zos.putNextEntry(anEntry);
            Files.copy(f, zos);
        }
    }

    /***
     * Extract zipfile to outdir with complete directory structure.
     *
     * @param zipfile Input .zip file
     * @param outdir Output directory
     */
    public static void unzip(final Path zipfile, final Path outdir) throws IOException {
        try(final ZipInputStream zin = new ZipInputStream(Files.newInputStream(zipfile))) {
            ZipEntry entry;
            String name, dir;
            while ((entry = zin.getNextEntry()) != null)
            {
                name = entry.getName();
                if(entry.isDirectory() ) {
                    Files.createDirectories(outdir.resolve(name));
                    continue;
                }

                dir = dirpart(name);
                if(dir != null) {
                    Files.createDirectories(outdir.resolve(name));
                }

                //extract file
                Files.copy(zin, outdir.resolve(name));
            }
        }
    }

    private static String dirpart(final String name) {
        final int s = name.lastIndexOf(File.separatorChar);
        return s == -1 ? null : name.substring( 0, s );
    }
}