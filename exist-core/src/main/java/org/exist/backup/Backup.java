/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.backup;

import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.Namespaces;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.start.CompatibleJavaVersionCheck;
import org.exist.start.StartException;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.FileUtils;
import org.exist.util.NamedThreadGroupFactory;
import org.exist.util.SystemExitCodes;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.*;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.XMLResource;

import javax.annotation.Nullable;
import javax.swing.*;
import javax.xml.transform.OutputKeys;
import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Backup {
    private static final String EXIST_GENERATED_FILENAME_DOT_FILENAME = "_eXist_generated_backup_filename_dot_file_";
    private static final String EXIST_GENERATED_FILENAME_DOTDOT_FILENAME = "_eXist_generated_backup_filename_dotdot_file_";

    private static final int BACKUP_FORMAT_VERSION = 2;

    private static final boolean DEFAULT_DEDUPLICATE_BLOBS_OPTION = false;

    private static final AtomicInteger backupThreadId = new AtomicInteger();
    private static final NamedThreadGroupFactory backupThreadGroupFactory = new NamedThreadGroupFactory("java-backup-tool");
    private final ThreadGroup backupThreadGroup = backupThreadGroupFactory.newThreadGroup(null);
    private final Properties defaultOutputProperties = new Properties();
    private final Properties contentsOutputProps = new Properties();
    private final Path target;
    private final XmldbURI rootCollection;
    private final String user;
    private final String pass;
    private final boolean deduplicateBlobs;

    public Backup(final String user, final String pass, final Path target) {
        this(user, pass, target, XmldbURI.LOCAL_DB_URI);
    }

    public Backup(final String user, final String pass, final Path target, final XmldbURI rootCollection) {
        this(user, pass, target, rootCollection, null);
    }

    public Backup(final String user, final String pass, final Path target, final XmldbURI rootCollection,
            @Nullable final Properties properties) {
        this(user, pass, target, rootCollection, properties, DEFAULT_DEDUPLICATE_BLOBS_OPTION);
    }

    public Backup(final String user, final String pass, final Path target, final XmldbURI rootCollection,
            @Nullable final Properties properties, final boolean deduplicateBlobs) {
        this.user = user;
        this.pass = pass;
        this.target = target;
        this.rootCollection = rootCollection;

        defaultOutputProperties.setProperty(OutputKeys.INDENT, "no");
        defaultOutputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultOutputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        defaultOutputProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "no");
        defaultOutputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");

        if (properties != null) {
            this.defaultOutputProperties.setProperty(OutputKeys.INDENT, properties.getProperty("indent", "no"));
        }
        this.contentsOutputProps.setProperty(OutputKeys.INDENT, "yes");
        this.deduplicateBlobs = deduplicateBlobs;
    }

    public static String encode(final String enco) {
        final StringBuilder out = new StringBuilder();
        char t;

        for (int y = 0; y < enco.length(); y++) {
            t = enco.charAt(y);

            if (t == '"') {
                out.append("&22;");
            } else if (t == '&') {
                out.append("&26;");
            } else if (t == '*') {
                out.append("&2A;");
            } else if (t == ':') {
                out.append("&3A;");
            } else if (t == '<') {
                out.append("&3C;");
            } else if (t == '>') {
                out.append("&3E;");
            } else if (t == '?') {
                out.append("&3F;");
            } else if (t == '\\') {
                out.append("&5C;");
            } else if (t == '|') {
                out.append("&7C;");
            } else {
                out.append(t);
            }
        }
        return (out.toString());
    }



    public static void main(final String[] args) {
        try {
            CompatibleJavaVersionCheck.checkForCompatibleJavaVersion();

            final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            final Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            final Backup backup = new Backup("admin", null, Paths.get("backup"), URIUtils.encodeXmldbUriFor(args[0]));
            backup.backup(false, null);
        } catch (final StartException e) {
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                System.err.println(e.getMessage());
            }
            System.exit(e.getErrorCode());
        } catch (final Throwable e) {
            e.printStackTrace();
            System.exit(SystemExitCodes.CATCH_ALL_GENERAL_ERROR_EXIT_CODE);
        }
    }

    public static void writeUnixStylePermissionAttributes(final AttributesImpl attr, final Permission permission) {
        if (permission == null) {
            return;
        }

        try {
            attr.addAttribute(Namespaces.EXIST_NS, "owner", "owner", "CDATA", permission.getOwner().getName());
            attr.addAttribute(Namespaces.EXIST_NS, "group", "group", "CDATA", permission.getGroup().getName());
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA", Integer.toOctalString(permission.getMode()));
        } catch (final Exception ignored) {

        }
    }

    public static void writeACLPermission(final SAXSerializer serializer, final ACLPermission acl) throws SAXException {
        if (acl == null) {
            return;
        }
        final AttributesImpl attr = new AttributesImpl();
        attr.addAttribute(Namespaces.EXIST_NS, "entries", "entries", "CDATA", Integer.toString(acl.getACECount()));
        attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", Short.toString(acl.getVersion()));

        serializer.startElement(Namespaces.EXIST_NS, "acl", "acl", attr);

        for (int i = 0; i < acl.getACECount(); i++) {
            attr.clear();
            attr.addAttribute(Namespaces.EXIST_NS, "index", "index", "CDATA", Integer.toString(i));
            attr.addAttribute(Namespaces.EXIST_NS, "target", "target", "CDATA", acl.getACETarget(i).name());
            attr.addAttribute(Namespaces.EXIST_NS, "who", "who", "CDATA", acl.getACEWho(i));
            attr.addAttribute(Namespaces.EXIST_NS, "access_type", "access_type", "CDATA", acl.getACEAccessType(i).name());
            attr.addAttribute(Namespaces.EXIST_NS, "mode", "mode", "CDATA", Integer.toOctalString(acl.getACEMode(i)));

            serializer.startElement(Namespaces.EXIST_NS, "ace", "ace", attr);
            serializer.endElement(Namespaces.EXIST_NS, "ace", "ace");
        }

        serializer.endElement(Namespaces.EXIST_NS, "acl", "acl");
    }

    public void backup(final boolean guiMode, final JFrame parent) throws XMLDBException, IOException, SAXException {
        final Collection current = DatabaseManager.getCollection(rootCollection.toString(), user, pass);

        if (guiMode) {
            final BackupDialog dialog = new BackupDialog(parent, false);
            dialog.setSize(new Dimension(350, 150));
            dialog.setVisible(true);
            final BackupRunnable backupRunnable = new BackupRunnable(current, dialog, this);
            final Thread backupThread = newBackupThread("backup-" + backupThreadId.getAndIncrement(), backupRunnable);
            backupThread.start();


            //super("exist-backupThread-" + backupThreadId.getAndIncrement());

            if (parent == null) {

                // if backup runs as a single dialog, wait for it (or app will terminate)
                while (backupThread.isAlive()) {

                    synchronized (this) {

                        try {
                            wait(20);
                        } catch (final InterruptedException ignored) {
                        }
                    }
                }
            }
        } else {
            backup(current, null);
        }
    }

    private void backup(final Collection current, final BackupDialog dialog) throws XMLDBException, IOException, SAXException {
        String cname = current.getName();

        if (cname.charAt(0) != '/') {
            cname = "/" + cname;
        }

        final FunctionE<String, BackupWriter, IOException> fWriter;
        if (FileUtils.fileName(target).endsWith(".zip")) {
            fWriter = currentName -> new ZipWriter(target, encode(URIUtils.urlDecodeUtf8(currentName)));
        } else {
            fWriter = currentName -> {
                String child = encode(URIUtils.urlDecodeUtf8(currentName));
                if (child.charAt(0) == '/') {
                    child = child.substring(1);
                }
                return new FileSystemWriter(target.resolve(child));
            };
        }

        final Set<String> seenBlobIds = new HashSet<>();
        try (final BackupWriter output = fWriter.apply(cname)) {
            backup(seenBlobIds, current, output, dialog);
        }
    }

    private void backup(final Set<String> seenBlobIds, final Collection current, final BackupWriter output, final BackupDialog dialog) throws XMLDBException, IOException, SAXException {
        if (current == null) {
            return;
        }

        current.setProperty(OutputKeys.ENCODING, defaultOutputProperties.getProperty(OutputKeys.ENCODING));
        current.setProperty(OutputKeys.INDENT, defaultOutputProperties.getProperty(OutputKeys.INDENT));
        current.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, defaultOutputProperties.getProperty(EXistOutputKeys.EXPAND_XINCLUDES));
        current.setProperty(EXistOutputKeys.PROCESS_XSL_PI, defaultOutputProperties.getProperty(EXistOutputKeys.PROCESS_XSL_PI));

        // get resources and permissions
        final String[] resources = current.listResources();

        // do not sort: order is important because permissions need to be read in the same order below
        // Arrays.sort( resources );

        final UserManagementService mgtService = (UserManagementService) current.getService("UserManagementService", "1.0");
        final Permission[] perms = mgtService.listResourcePermissions();
        final Permission currentPerms = mgtService.getPermissions(current);


        if (dialog != null) {
            dialog.setCollection(current.getName());
            dialog.setResourceCount(resources.length);
        }
        final Writer contents = output.newContents();

        // serializer writes to __contents__.xml
        final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        serializer.setOutput(contents, contentsOutputProps);

        serializer.startDocument();
        serializer.startPrefixMapping("", Namespaces.EXIST_NS);

        // write <collection> element
        final EXistCollection cur = (EXistCollection) current;
        final AttributesImpl attr = new AttributesImpl();

        //The name should have come from an XmldbURI.toString() call
        attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", current.getName());
        writeUnixStylePermissionAttributes(attr, currentPerms);
        attr.addAttribute(Namespaces.EXIST_NS, "created", "created", "CDATA", "" + new DateTimeValue(cur.getCreationTime()));
        attr.addAttribute(Namespaces.EXIST_NS, "deduplicate-blobs", "deduplicate-blobs", "CDATA", Boolean.toString(deduplicateBlobs));
        attr.addAttribute(Namespaces.EXIST_NS, "version", "version", "CDATA", String.valueOf(BACKUP_FORMAT_VERSION));

        serializer.startElement(Namespaces.EXIST_NS, "collection", "collection", attr);

        if (currentPerms instanceof ACLPermission) {
            writeACLPermission(serializer, (ACLPermission) currentPerms);
        }

        // scan through resources
        Resource resource;
        OutputStream os;
        BufferedWriter writer;
        SAXSerializer contentSerializer;

        for (int i = 0; i < resources.length; i++) {

            try {

                if ("__contents__.xml".equals(resources[i])) {

                    //Skipping resources[i]
                    continue;
                }

                resource = current.getResource(resources[i]);

                if (dialog != null) {
                    dialog.setResource(resources[i]);
                    dialog.setProgress(i);
                }

                // Avoid NPE
                if (resource == null) {
                    final String msg = "Resource " + resources[i] + " could not be found.";

                    if(dialog != null) {
                        Object[] options = {"Ignore", "Abort"};
                        int n = JOptionPane.showOptionDialog(null, msg, "Backup Error",
                                JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                                options, options[1]);
                        if (n == JOptionPane.YES_OPTION) {
                            // ignore one
                            continue;
                        }

                        // Abort
                        dialog.dispose();
                        JOptionPane.showMessageDialog(null, "Backup aborted.", "Abort", JOptionPane.WARNING_MESSAGE);
                    }
                    throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, msg);
                }

                final String name = resources[i];
                String filename = encode(URIUtils.urlDecodeUtf8(resources[i]));

                // Check for special resource names which cause problems as filenames, and if so, replace the filename with a generated filename

                if (".".equals(name.trim())) {
                    filename = EXIST_GENERATED_FILENAME_DOT_FILENAME + i;
                } else if ("..".equals(name.trim())) {
                    filename = EXIST_GENERATED_FILENAME_DOTDOT_FILENAME + i;
                }

                if (resource instanceof ExtendedResource) {
                    if (deduplicateBlobs && resource instanceof EXistBinaryResource) {
                        // only add distinct blobs to the Blob Store once!
                        final String blobId = ((EXistBinaryResource)resource).getBlobId().toString();
                        if (!seenBlobIds.contains(blobId)) {
                            os = output.newBlobEntry(blobId);
                            ((ExtendedResource) resource).getContentIntoAStream(os);
                            output.closeEntry();

                            seenBlobIds.add(blobId);
                        }
                    } else {
                        os = output.newEntry(filename);
                        ((ExtendedResource)resource).getContentIntoAStream(os);
                        output.closeEntry();
                    }
                } else {
                    os = output.newEntry(filename);
                    writer = new BufferedWriter(new OutputStreamWriter(os, UTF_8));

                    // write resource to contentSerializer
                    contentSerializer = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
                    contentSerializer.setOutput( writer, defaultOutputProperties );
                    ( (EXistResource)resource ).setLexicalHandler( contentSerializer );
                    ( (XMLResource)resource ).getContentAsSAX( contentSerializer );
                    SerializerPool.getInstance().returnObject( contentSerializer );

                    writer.flush();
                    output.closeEntry();
                }
                final EXistResource ris = (EXistResource)resource;

                //store permissions
                attr.clear();
                attr.addAttribute(Namespaces.EXIST_NS, "type", "type", "CDATA", resource.getResourceType());
                attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", name);
                writeUnixStylePermissionAttributes(attr, perms[i]);
                Date date = ris.getCreationTime();

                if (date != null) {
                    attr.addAttribute(Namespaces.EXIST_NS, "created", "created", "CDATA", "" + new DateTimeValue(date));
                }
                date = ris.getLastModificationTime();

                if (date != null) {
                    attr.addAttribute(Namespaces.EXIST_NS, "modified", "modified", "CDATA", "" + new DateTimeValue(date));
                }

                attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA", filename);
                attr.addAttribute(Namespaces.EXIST_NS, "mimetype", "mimetype", "CDATA", encode(((EXistResource) resource).getMimeType()));

                if (!"BinaryResource".equals(resource.getResourceType())) {

                    if (ris.getDocType() != null) {

                        if (ris.getDocType().getName() != null) {
                            attr.addAttribute(Namespaces.EXIST_NS, "namedoctype", "namedoctype", "CDATA", ris.getDocType().getName());
                        }

                        if (ris.getDocType().getPublicId() != null) {
                            attr.addAttribute(Namespaces.EXIST_NS, "publicid", "publicid", "CDATA", ris.getDocType().getPublicId());
                        }

                        if (ris.getDocType().getSystemId() != null) {
                            attr.addAttribute(Namespaces.EXIST_NS, "systemid", "systemid", "CDATA", ris.getDocType().getSystemId());
                        }
                    }
                } else {
                    attr.addAttribute( Namespaces.EXIST_NS, "blob-id", "blob-id", "CDATA", ((EXistBinaryResource)ris).getBlobId().toString());
                }

                serializer.startElement(Namespaces.EXIST_NS, "resource", "resource", attr);
                if (perms[i] instanceof ACLPermission) {
                    writeACLPermission(serializer, (ACLPermission) perms[i]);
                }
                serializer.endElement(Namespaces.EXIST_NS, "resource", "resource");
            } catch (final XMLDBException e) {
                System.err.println("Failed to backup resource " + resources[i] + " from collection " + current.getName());
                throw e;
            }
        }

        // write subcollections
        final String[] collections = current.listChildCollections();

        for (final String collection : collections) {

            if (current.getName().equals(XmldbURI.SYSTEM_COLLECTION) && "temp".equals(collection)) {
                continue;
            }
            attr.clear();
            attr.addAttribute(Namespaces.EXIST_NS, "name", "name", "CDATA", collection);
            attr.addAttribute(Namespaces.EXIST_NS, "filename", "filename", "CDATA", encode(URIUtils.urlDecodeUtf8(collection)));
            serializer.startElement(Namespaces.EXIST_NS, "subcollection", "subcollection", attr);
            serializer.endElement(Namespaces.EXIST_NS, "subcollection", "subcollection");
        }

        // close <collection>
        serializer.endElement(Namespaces.EXIST_NS, "collection", "collection");
        serializer.endPrefixMapping("");
        serializer.endDocument();
        output.closeContents();

        SerializerPool.getInstance().returnObject(serializer);

        // descend into subcollections
        Collection child;

        for (final String collection : collections) {
            child = current.getChildCollection(collection);

            if (child.getName().equals(XmldbURI.TEMP_COLLECTION)) {
                continue;
            }
            output.newCollection(encode(URIUtils.urlDecodeUtf8(collection)));
            backup(seenBlobIds, child, output, dialog);
            output.closeCollection();
        }
    }

    /**
     * Create a new thread for this backup instance.
     *
     * @param threadName the name of the thread
     * @param runnable   the function to execute on the thread
     * @return the thread
     */
    private Thread newBackupThread(final String threadName, final Runnable runnable) {
        return new Thread(backupThreadGroup, runnable, backupThreadGroup.getName() + "." + threadName);
    }

    private static class BackupRunnable implements Runnable {
        private final Collection collection;
        private final BackupDialog dialog;
        private final Backup backup;

        public BackupRunnable(final Collection collection, final BackupDialog dialog, final Backup backup) {
            this.collection = collection;
            this.dialog = dialog;
            this.backup = backup;
        }

        @Override
        public void run() {
            try {
                backup.backup(collection, dialog);
                dialog.setVisible(false);
            } catch (final Exception e) {
                e.printStackTrace();
            }
        }
    }
}
