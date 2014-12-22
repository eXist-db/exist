package org.exist.repo;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Utility methods for backing up and restoring the expath file system repository.
 */
public class RepoBackup {

    public final static String REPO_ARCHIVE = "expathrepo.zip";

    public static File backup(DBBroker broker) throws IOException {
        ZipOutputStream os = null;
        File tempFile = null;
        try {
            final File directory = ExistRepository.getRepositoryDir(broker.getConfiguration());
            tempFile = File.createTempFile("expathrepo", "zip");
            os = new ZipOutputStream(new FileOutputStream(tempFile));

            zipDir(directory.getAbsolutePath(), os, "");
        } finally {
            if (os != null)
                {os.close();}
        }
        return tempFile;
    }

    public static void restore(DBBroker broker) throws IOException, PermissionDeniedException {
        final XmldbURI docPath = XmldbURI.createInternal(XmldbURI.ROOT_COLLECTION + "/" + REPO_ARCHIVE);
        DocumentImpl doc = null;
        try {
            doc = broker.getXMLResource(docPath, Lock.READ_LOCK);
            if (doc == null)
                {return;}
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                {throw new IOException(docPath + " is not a binary resource");}

            final File file = ((NativeBroker)broker).getCollectionBinaryFileFsPath(doc.getURI());
            final File directory = ExistRepository.getRepositoryDir(broker.getConfiguration());
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
    public static void zipDir(String directory, ZipOutputStream zos, String path)
            throws IOException {
        final File zipDir = new File(directory);
        // get a listing of the directory content
        final String[] dirList = zipDir.list();
        final byte[] readBuffer = new byte[2156];
        int bytesIn = 0;
        // loop through dirList, and zip the files
        for (int i = 0; i < dirList.length; i++) {
            final File f = new File(zipDir, dirList[i]);
            if (f.isDirectory()) {
                final String filePath = f.getPath();
                zipDir(filePath, zos, path + f.getName() + "/");
                continue;
            }
            final FileInputStream fis = new FileInputStream(f);
            try {
                final ZipEntry anEntry = new ZipEntry(path + f.getName());
                zos.putNextEntry(anEntry);
                bytesIn = fis.read(readBuffer);
                while (bytesIn != -1) {
                    zos.write(readBuffer, 0, bytesIn);
                    bytesIn = fis.read(readBuffer);
                }
            } finally {
                fis.close();
            }
        }
    }

    /***
     * Extract zipfile to outdir with complete directory structure.
     *
     * @param zipfile Input .zip file
     * @param outdir Output directory
     */
    public static void unzip(File zipfile, File outdir) throws IOException {
        ZipInputStream zin = null;
        try
        {
            zin = new ZipInputStream(new FileInputStream(zipfile));
            ZipEntry entry;
            String name, dir;
            while ((entry = zin.getNextEntry()) != null)
            {
                name = entry.getName();
                if( entry.isDirectory() )
                {
                    mkdirs(outdir, name);
                    continue;
                }
                dir = dirpart(name);
                if( dir != null )
                    {mkdirs(outdir, dir);}

                extractFile(zin, outdir, name);
            }
        } finally {
            if (zin != null)
                try {
                    zin.close();
                } catch (final IOException e) {
                    // ignore
                }
        }
    }

    private static void extractFile(ZipInputStream in, File directory, String name) throws IOException
    {
        final byte[] buf = new byte[4096];
        final OutputStream out = new FileOutputStream(new File(directory, name));
        int count;
        try {
            while ((count = in.read(buf)) != -1) {
                out.write(buf, 0, count);
            }
        } finally {
            out.close();
        }
    }

    private static void mkdirs(File directory,String path)
    {
        final File d = new File(directory, path);
        if( !d.exists() )
            {d.mkdirs();}
    }

    private static String dirpart(String name)
    {
        final int s = name.lastIndexOf( File.separatorChar );
        return s == -1 ? null : name.substring( 0, s );
    }
}