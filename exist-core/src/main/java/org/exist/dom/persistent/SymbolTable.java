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
package org.exist.dom.persistent;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.dom.QName;
import org.exist.storage.*;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Maintains a global symbol table shared by a database instance. The symbol
 * table maps namespace URIs and node names to unique, numeric ids. Internally,
 * the db does not store node QNames in clear text. Instead, it uses the numeric ids
 * maintained here.
 *
 * The global SymbolTable singleton can be retrieved from {@link org.exist.storage.BrokerPool#getSymbols()}.
 * It is saved into the database file "symbols.dbx".
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SymbolTable implements BrokerPoolService, Closeable {

    private static final Logger LOG = LogManager.getLogger(SymbolTable.class);

    private static final String FILE_NAME = "symbols.dbx";

    public static final short FILE_FORMAT_VERSION_ID = 8;
    public static final short LEGACY_FILE_FORMAT_VERSION_ID = 7;

    public enum SymbolType {
        NAME((byte) 0),
        NAMESPACE((byte) 1),
        MIMETYPE((byte) 2);

        private final byte typeId;
        private SymbolType(final byte typeId) {
            this.typeId = typeId;
        }

        public final byte getTypeId() {
            return typeId;
        }

        public static SymbolType valueOf(final byte typeId) {
            for(final SymbolType symbolType : SymbolType.values()) {
                if(symbolType.getTypeId() == typeId) {
                    return symbolType;
                }
            }
            throw new IllegalArgumentException("No such enumerated value for typeId:" + typeId);
        }
    }

    public static final int LENGTH_LOCAL_NAME = 2; //sizeof short
    public static final int LENGTH_NS_URI = 2; //sizeof short

    public static final char ATTR_NAME_PREFIX = '@';

    protected final SymbolCollection localNameSymbols = new LocalNameSymbolCollection(SymbolType.NAME, 200);
    protected final SymbolCollection namespaceSymbols = new SymbolCollection(SymbolType.NAMESPACE, 200);
    protected final SymbolCollection mimeTypeSymbols = new SymbolCollection(SymbolType.MIMETYPE, 32);

    /**
     * Temporary name pool to share QName instances during indexing.
     */
    private final QNamePool namePool = new QNamePool();

    /**
     * set to true if the symbol table needs to be saved
     */
    private boolean changed = false;

    /**
     * the underlying symbols.dbx file
     */
    private Path file;
    private final VariableByteOutputStream outBuffer = new VariableByteOutputStream(256);

    /**
     * the channel new entries are appended to, opened on the first new entry
     */
    private @Nullable FileChannel channel = null;

    /**
     * the file key ({@link BasicFileAttributes#fileKey()}) of the file as eXist-db last wrote or read it, if the file
     * system has one
     */
    private @Nullable Object fileKey = null;

    /**
     * the size of the file as eXist-db last wrote or read it, with the entries appended to it since; -1 before the
     * file is loaded
     */
    private long fileSize = -1;

    /**
     * true if entries were appended since the file was last forced to disk
     */
    /** Whether entries were appended since the file was last forced; read without the lock by {@link #sync()}. */
    private volatile boolean unsynced = false;
    /** Serializes {@link #sync()}, apart from the table's own lock, which looking up symbols takes. */
    private final Object syncLock = new Object();

    /**
     * true if a write to the file failed, so that it may hold part of an entry: it is written again in full
     */
    private boolean needsRewrite = false;

    private @Nullable BrokerPool pool = null;
    private boolean warnedReadOnly = false;
    private boolean warnedRewrite = false;

    @Override
    public void configure(final Configuration configuration) {
        final Path dataDir = (Path) configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        this.file = dataDir.resolve(getFileName());
    }

    @Override
    public void prepare(final BrokerPool pool) throws BrokerPoolServiceException {
        this.pool = pool;
        try {
            if (!Files.isReadable(file)) {
                saveSymbols();
            } else {
                loadSymbols();
            }
            // what the file is now, to tell later whether something else replaced or changed it
            recordFileAttributes();
        } catch(final EXistException | IOException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void stopSystem(final DBBroker systemBroker) throws BrokerPoolServiceException {
        try {
            close();
        } catch (final IOException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    public static final String getFileName() {
        return FILE_NAME;
    }

    /**
     * Retrieve a shared QName instance from the temporary pool.
     *
     * TODO: make the namePool thread-local to avoid synchronization.
     * @param type qname type
     * @param namespaceURI qname namespace uri
     * @param localName qname localname
     * @param prefix qname prefix
     * @return qname from pool
     */
    public synchronized QName getQName(final short type, final String namespaceURI, final String localName, final String prefix) {
        final byte itype = type == Node.ATTRIBUTE_NODE ? ElementValue.ATTRIBUTE : ElementValue.ELEMENT;
        QName qn = namePool.get(itype, namespaceURI, localName, prefix);
        if(qn == null) {
            qn = namePool.add(itype, namespaceURI, localName, prefix);
        }
        return qn;
    }

    /**
     * Return a unique id for the local node name of the specified element.
     *
     * @param element the element to create a unique id for
     * @return unique id for the local node name of the specified element.
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getSymbol(final Element element) {
        return (short) localNameSymbols.getId(element.getLocalName());
    }

    /**
     * Return a unique id for the local node name of the specified attribute.
     *
     * @param attr the attribute to create a unique id for
     * @return unique id for the local node name of the specified attribute.
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getSymbol(final Attr attr) {
        final String key = ATTR_NAME_PREFIX + attr.getLocalName();
        return (short) localNameSymbols.getId(key);
    }

    /**
     * Returns a unique id for the specified local name. If the name is
     * the local name of an attribute, it should start with a '@' character.
     *
     * @param name local name
     * @return unique id for local name
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getSymbol(final String name) {
        if(name.isEmpty()) {
            throw new IllegalArgumentException("name is empty");
        }
        return (short) localNameSymbols.getId(name);
    }

    /**
     * Returns a unique id for the specified namespace URI.
     *
     * @param ns namespace uri
     * @return unique id for namespace uri
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getNSSymbol(final String ns) {
        if(ns == null || ns.isEmpty()) {
            return 0;
        }
        return (short) namespaceSymbols.getId(ns);
    }

    public synchronized int getMimeTypeId(final String mimeType) {
        return mimeTypeSymbols.getId(mimeType);
    }

    /**
     * @return true if the symbol table needs to be saved to persistent storage.
     *
     */
    public synchronized boolean hasChanged() {
        return changed;
    }

    /**
     * Returns the local name registered for the id or
     * null if the name is not known.
     *
     * @param id identifier
     * @return the local name registered for the id or null if the name is not known.
     */
    public synchronized String getName(final short id) {
        return localNameSymbols.getSymbol(id);
    }

    public synchronized String getMimeType(final int id) {
        return mimeTypeSymbols.getSymbol(id);
    }

    /**
     * Returns the namespace URI registered for the id or null
     * if the namespace URI is not known. Returns the empty string
     * if the namespace is empty.
     *
     * @param id identifier
     * @return  the namespace URI registered for the id or null
     */
    public synchronized String getNamespace(final short id) {
        return namespaceSymbols.getSymbol(id);
    }

    /**
     * Write the symbol table to persistent storage. Only called when upgrading
     * a .dbx file from previous versions.
     *
     * @param os outputstream
     * @throws IOException in response to an IO error
     */
    private synchronized void writeAll(final VariableByteOutputStream os) throws IOException {
        os.writeFixedInt(FILE_FORMAT_VERSION_ID);
        localNameSymbols.write(os);
        namespaceSymbols.write(os);
        mimeTypeSymbols.write(os);
        changed = false;
    }

    /**
     * Read the symbol table from disk.
     *
     * @param is input
     * @throws IOException in response to an IO error
     */
    protected final void read(final VariableByteInput is) throws IOException {
        localNameSymbols.clear();
        namespaceSymbols.clear();
        mimeTypeSymbols.clear();
        while(is.available() > 0) {
            if (!readEntry(is)) {
                // a crash while the entry was appended: the table is complete without it, as the entry was
                // not forced to disk, so neither was anything that uses its symbol
                LOG.warn("{} ends in an incomplete entry, which is ignored; the table will be written again in "
                        + "full", getFile().toAbsolutePath());
                needsRewrite = true;
                break;
            }
        }
    }

    /**
     * Keeps the id of an incomplete entry from being given to a new symbol, if its type and id could be read: nothing
     * refers to it, as it never reached the disk, but nothing needs to rely on that.
     */
    private void reserve(final byte type, final int id) {
        if (id <= 0 || id > Short.MAX_VALUE) {
            return;
        }
        final SymbolCollection collection;
        try {
            collection = switch (SymbolType.valueOf(type)) {
                case NAME -> localNameSymbols;
                case NAMESPACE -> namespaceSymbols;
                case MIMETYPE -> mimeTypeSymbols;
            };
        } catch (final IllegalArgumentException e) {
            return;
        }
        if (id > collection.offset) {
            collection.symbolsById = collection.ensureCapacity(collection.symbolsById, id);
            collection.offset = (short) id;
        }
    }

    /**
     * Reads an entry.
     *
     * @return false if the file ends before the entry does
     */
    private boolean readEntry(final VariableByteInput is) throws IOException {
        byte type = -1;
        int id = -1;
        final String key;
        try {
            type = is.readByte();
            id = is.readInt();
            final int length = is.readInt();
            if (length < 0 || length > is.available()) {
                reserve(type, id);
                return false;
            }
            final byte[] data = new byte[length];
            if (length > 0 && is.read(data) < length) {
                reserve(type, id);
                return false;
            }
            key = new String(data, StandardCharsets.UTF_8);
        } catch (final EOFException e) {
            reserve(type, id);
            return false;
        }
        //symbol types can be written in any order by SymbolCollection.getById()->SymbolCollection.write()
        switch(SymbolType.valueOf(type)) {
            case NAME:
                localNameSymbols.add(id, key);
                break;
            case NAMESPACE:
                namespaceSymbols.add(id, key);
                break;
            case MIMETYPE:
                mimeTypeSymbols.add(id, key);
                break;
            //Removed default clause
        }
        return true;
    }

    /**
     * Legacy method: read a symbol table written by a previous eXist version.
     *
     * @param istream input
     * @throws IOException in response to an IO error
     */
    protected final void readLegacy(final VariableByteInput istream) throws IOException {
        istream.readShort(); //read max, not needed anymore
        istream.readShort(); //read nsMax not needed anymore
        String key;
        short id;
        //read local names
        int count = istream.readInt();
        for(int i = 0; i < count; i++) {
            key = istream.readUTF();
            id = istream.readShort();
            localNameSymbols.add(id, key);
        }
        //read namespaces
        count = istream.readInt();
        for(int i = 0; i < count; i++) {
            key = istream.readUTF();
            id = istream.readShort();
            namespaceSymbols.add(id, key);
        }
        // default mappings have been removed
        // read them for backwards compatibility
        count = istream.readInt();
        for(int i = 0; i < count; i++) {
            istream.readUTF();
            istream.readShort();
        }
        //read namespaces
        count = istream.readInt();
        int mimeId;
        for(int i = 0; i < count; i++) {
            key = istream.readUTF();
            mimeId = istream.readInt();
            mimeTypeSymbols.add(mimeId, key);
        }
        changed = false;
    }

    public final Path getFile() {
        return file;
    }

    /**
     * Save the entire symbol table. Will only be called when initializing an
     * empty database or when upgrading an older dbx file.
     *
     * @throws EXistException in response to eXist-db error
     */
    private void saveSymbols() throws EXistException {
        try {
            writeTable();
        } catch(final IOException e) {
            throw new EXistException("IO error occurred while creating "
                + this.getFile().toAbsolutePath(), e);
        }
    }

    /**
     * Writes the entire symbol table to a new file, forces it to disk, and moves it in place of the file, so that
     * the file is never partly written: a crash leaves either the earlier file or the new one.
     */
    private void writeTable() throws IOException {
        final Path path = getFile();
        final Path temp = path.resolveSibling(path.getFileName() + ".tmp");
        try (final VariableByteOutputStream os = new VariableByteOutputStream(8192);
                final FileChannel out = FileChannel.open(temp, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writeAll(os);
            writeFully(out, os.toByteArray());
            out.force(true);
        }
        try {
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (final AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
        syncDirectory(path.getParent());
    }

    /**
     * Forces the directory to disk, so that the file moved into it is there after a crash. Not every platform can
     * open a directory, Windows for one; there, the move is left to the file system.
     */
    private static void syncDirectory(final Path dir) {
        try (final FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (final IOException e) {
            LOG.debug("Could not force the directory {} to disk: {}", dir, e.getMessage());
        }
    }

    private static void writeFully(final FileChannel out, final byte[] bytes) throws IOException {
        final ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            out.write(buffer);
        }
    }

    /**
     * Read the global symbol table. The global symbol table stores QNames and
     * namespace/prefix mappings.
     *
     * @throws EXistException in response to eXist-db error
     */
    private synchronized void loadSymbols() throws EXistException {
        try(final InputStream fis = new BufferedInputStream(Files.newInputStream(getFile()))) {

            final VariableByteInput is = new VariableByteInputStream(fis);
            final int magic = is.readFixedInt();
            if(magic == LEGACY_FILE_FORMAT_VERSION_ID) {
                LOG.info("Converting legacy symbols.dbx to new format...");
                readLegacy(is);
                saveSymbols();
            } else if(magic != FILE_FORMAT_VERSION_ID) {
                throw new EXistException("Symbol table was created by an older" +
                    "or newer version of eXist" + " (file id: " + magic + "). " +
                    "To avoid damage, the database will stop.");
            } else {
                read(is);
            }
        } catch(final FileNotFoundException e) {
            throw new EXistException("Could not read " + this.getFile().toAbsolutePath(), e);
        } catch(final IOException e) {
            throw new EXistException("IO error occurred while reading "
                + this.getFile().toAbsolutePath() + ": " + e.getMessage(), e);
        } catch (final RuntimeException e) {
            // e.g. an entry of an unknown type, in a file that is damaged other than at its end
            throw new EXistException("Could not read " + this.getFile().toAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public void backupSymbolsTo(final OutputStream os) throws IOException {
        Files.copy(getFile(), os);
    }

    public void backupToArchive(final RawDataBackup backup) throws IOException {
        // do not use try-with-resources here, closing the OutputStream will close the entire backup
        //try(final OutputStream os = backup.newEntry(FileUtils.fileName(getFile()))) {
        try {
            final OutputStream os = backup.newEntry(FileUtils.fileName(getFile()));
            backupSymbolsTo(os);
        } finally {
            backup.closeEntry();
        }
    }

    public void flush() throws EXistException {
        //Noting to do ? -pb
    }

    /**
     * Appends an entry to the file. It is forced to disk by {@link #sync()}, before the journal is written.
     *
     * @param entry the entry
     * @throws IOException if the entry cannot be written
     */
    private void append(final byte[] entry) throws IOException {
        if (needsRewrite) {
            if (!warnedRewrite) {
                // once, until it succeeds: in a read-only database, it is tried again for each new symbol
                warnedRewrite = true;
                LOG.warn("Writing the symbol table {} again in full, as it may end in part of an entry",
                        getFile().toAbsolutePath());
            }
            rewrite();
        } else {
            checkFile();
        }
        if (channel == null) {
            channel = FileChannel.open(getFile(), StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        }
        try {
            writeFully(channel, entry);
        } catch (final IOException e) {
            // the file may now end in part of the entry
            needsRewrite = true;
            throw e;
        }
        fileSize += entry.length;
        unsynced = true;
    }

    /**
     * Forces the entries appended since the last sync to disk. It is called before anything that may refer to the
     * new symbols reaches the operating system: the journal calls it before it writes its records, so that recovery
     * can always resolve them after a crash (#2220), and {@link org.exist.storage.btree.Paged} before it writes a
     * page of any file, journaled or not. Forcing once per such write, rather than once per new symbol, keeps loading
     * many new names fast, and costs an uncontended lock when no symbol was added since. The symbol table is not locked
     * while the file is forced, so that looking up symbols doesn't wait for the disk; callers of this method do wait
     * for a force under way, so that none goes on to write before the symbols are on disk.
     *
     * @throws IOException if the file cannot be forced to disk
     */
    public void sync() throws IOException {
        // one sync at a time, the flag checked under the same lock: a caller that finds nothing to force returns only
        // once a force already under way is done, so that what it writes next can't reach the disk before the symbols
        synchronized (syncLock) {
            if (!unsynced) {
                // nothing appended since the last sync: the common case, for every page written
                return;
            }
            final FileChannel toForce;
            synchronized (this) {
                if (!unsynced || channel == null) {
                    return;
                }
                toForce = channel;
                unsynced = false;
            }
            try {
                toForce.force(false);
            } catch (final ClosedChannelException e) {
                // the file is being written again in full, and forced, under the lock: wait for it to be done
                synchronized (this) {
                    LOG.debug("Symbol table {} written again while it was being forced to disk", getFile());
                }
            } catch (final IOException e) {
                synchronized (this) {
                    unsynced = true;
                }
                throw e;
            }
        }
    }

    /**
     * @return true if entries were appended since the file was last forced to disk
     */
    public synchronized boolean hasUnsyncedEntries() {
        return unsynced;
    }

    /**
     * A file's key, where the file system has one, and size; no key and a size of -1 if there is no file.
     *
     * @param key the file key ({@link BasicFileAttributes#fileKey()})
     * @param size the file's size in bytes
     */
    private record FileAttributes(@Nullable Object key, long size) {
    }

    /**
     * @return the key and size of the file at the path
     */
    private FileAttributes probeFileAttributes() throws IOException {
        try {
            final BasicFileAttributes attributes = Files.readAttributes(getFile(), BasicFileAttributes.class);
            return new FileAttributes(attributes.fileKey(), attributes.size());
        } catch (final NoSuchFileException e) {
            return new FileAttributes(null, -1);
        }
    }

    /**
     * Records the file's key and size, as eXist-db has just written or read it.
     */
    private void recordFileAttributes() throws IOException {
        final FileAttributes attributes = probeFileAttributes();
        fileKey = attributes.key();
        fileSize = attributes.size();
    }

    /**
     * Checks that the file at the path is still the one eXist-db wrote or read, with all of the entries appended
     * since. If something outside eXist-db replaced or changed it while the database was running, as a file
     * synchronization or an overlay file system can, the entries it lacks would otherwise be lost: the table is then
     * written again from memory, where it is complete.
     */
    private void checkFile() throws IOException {
        final Path path = getFile();
        // a deleted file has a size of -1, and is written again below
        final FileAttributes attributes = probeFileAttributes();
        final Object key = attributes.key();
        final long size = attributes.size();
        if (size >= 0 && Objects.equals(key, fileKey) && size == fileSize) {
            return;
        }
        LOG.error("{} was replaced or changed by something other than eXist-db while the database was running "
                + "(file key {}, size {}; expected file key {}, size {}). Writing the symbol table again from memory. "
                + "Do not keep the data directory in a folder that is synchronized, snapshotted or on an overlay "
                + "file system.", path.toAbsolutePath(), key, size, fileKey, fileSize);
        rewrite();
    }

    /**
     * Writes the entire table to the file again, from memory. If that fails, the next append tries again.
     */
    private void rewrite() throws IOException {
        needsRewrite = true;
        if (channel != null) {
            final FileChannel previous = channel;
            channel = null;
            previous.close();
        }
        writeTable();
        recordFileAttributes();
        unsynced = false;
        needsRewrite = false;
        warnedRewrite = false;
    }

    /**
     * Whether the database is read-only, in which case a new symbol that can't be written is kept in memory only:
     * queries may look up names that aren't in the table, and must not fail because the file can't be written.
     */
    private boolean isReadOnly() {
        return pool != null && pool.isReadOnly();
    }

    @Override
    public synchronized void close() throws IOException {
        outBuffer.close();
        try {
            if (fileSize >= 0) {
                if (!isReadOnly()) {
                    if (needsRewrite) {
                        rewrite();
                    } else {
                        checkFile();
                    }
                }
                // also in a read-only database, for what was appended before it became read-only; forced here, under
                // the table's lock, not with sync(), which takes its own lock first and would deadlock with this one
                if (unsynced && channel != null) {
                    channel.force(false);
                    unsynced = false;
                }
            }
        } finally {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        }
    }

    /**
     * Represents a distinct collection of symbols
     *
     * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
     * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
     */
    protected class SymbolCollection {

        private final SymbolType symbolType;

        /**
         * Maps mimetype names to an integer id (persisted to disk)
         */
        private final Object2IntMap<String> symbolsByName;

        /**
         * Maps int ids to mimetype names (transient map for fast reverse lookup of symbolsByName)
         */
        private String[] symbolsById;

        /**
         * contains the offset of the last symbol
         */
        protected short offset = 0;

        public SymbolCollection(final SymbolType symbolType, final int initialSize) {
            this.symbolType = symbolType;
            symbolsByName = new Object2IntOpenHashMap<>(initialSize);
            symbolsByName.defaultReturnValue(-1);
            symbolsById = new String[initialSize];
        }

        private SymbolType getSymbolType() {
            return symbolType;
        }

        private int add(final int id, final String name) {
            symbolsById = ensureCapacity(symbolsById, id);
            addSymbolById(id, name);
            addSymbolByName(name, id);
            if(id > offset) {
                offset = (short) id;
            }
            return id;
        }

        protected void addSymbolById(final int id, final String name) {
            symbolsById[id] = name;
        }

        protected void addSymbolByName(final String name, final int id) {
            symbolsByName.put(name, id);
        }

        protected String[] ensureCapacity(final String[] array, final int max) {
            if(array.length <= max) {
                final String[] newArray = new String[(max * 3) / 2];
                System.arraycopy(array, 0, newArray, 0, array.length);
                return newArray;
            }
            return array;
        }

        private void clear() {
            offset = 0;
        }

        public synchronized String getSymbol(final int id) {
            if(id <= 0 || id > offset) {
                return ""; //TODO : raise an exception ? -pb
            }
            // null for the id of an incomplete entry, which is reserved
            final String symbol = symbolsById[id];
            return symbol != null ? symbol : "";
        }

        public synchronized int getId(final String name) {
            int id = symbolsByName.getInt(name);
            if(id != -1) {
                return id;
            }
            // symbol space exceeded. return -1 to indicate.
            if(offset == Short.MAX_VALUE) {
                return -1;
            }

            // ids start at 1, not 0
            id = offset + 1;
            try {
                // the entry is written before the id is used (#2220)
                write(id, name);
            } catch (final UncheckedIOException e) {
                if (!isReadOnly()) {
                    throw e;
                }
                // a read-only database stores nothing: a name a query looks up is kept in memory only
                if (!warnedReadOnly) {
                    warnedReadOnly = true;
                    LOG.warn("The database is read-only: new symbols, such as '{}', are kept in memory only: {}",
                            name, e.getMessage());
                }
            }
            add(id, name);
            changed = true;
            return id;
        }

        protected final void write(final VariableByteOutputStream os) throws IOException {
            for (final String symbol : symbolsByName.keySet()) {
                final int id = symbolsByName.getInt(symbol);
                if (id < 0) {
                    LOG.error("Symbol Table: symbolTypeId={}, symbol='{}', id={}", getSymbolType(), symbol, id);
                    //TODO : raise exception ? -pb
                }
                writeEntry(id, symbol, os);
            }
        }

        // Append a new entry to the .dbx file
        private void write(final int id, final String key) {
            outBuffer.clear();
            try {
                writeEntry(id, key, outBuffer);
                append(outBuffer.toByteArray());
            } catch(final IOException e) {
                // the symbol must not be used: what refers to it could not be read again
                throw new UncheckedIOException("Symbol table: unable to write the symbol '" + key + "' to "
                        + getFile().toAbsolutePath() + ": " + e.getMessage(), e);
            }
        }

        private void writeEntry(final int id, final String key, final VariableByteOutputStream os) throws IOException {
            os.writeByte(getSymbolType().getTypeId());
            os.writeInt(id);
            os.writeUTF(key);
        }
    }

    /**
     * Local name storage is used by both element names and attribute names
     *
     * Attributes behave slightly differently to element names
     * For the persistent map symbolsByName, the attribute name is prefixed with
     * an '@' symbol to differentiate the attribute name from a similar element name
     * However, for the in-memory reverse map symbolsById, the attribute name
     * should not be prefixed.
     *
     * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
     */
    private class LocalNameSymbolCollection extends SymbolCollection {

        public LocalNameSymbolCollection(final SymbolType symbolType, final int initialSize) {
            super(symbolType, initialSize);
        }

        @Override
        protected void addSymbolById(final int id, final String name) {
            /*
             For attributes, Don't store '@' in in-memory mapping of id -> attrName
             enables faster retrieval
             */
            if(name.charAt(0) == ATTR_NAME_PREFIX) {
                super.addSymbolById(id, name.substring(1));
            } else {
                super.addSymbolById(id, name);
            }
        }
    }
}
