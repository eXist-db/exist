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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

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
    private OutputStream os = null;

    @Override
    public void configure(final Configuration configuration) {
        final Path dataDir = (Path) configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        this.file = dataDir.resolve(getFileName());
    }

    @Override
    public void prepare(final BrokerPool pool) throws BrokerPoolServiceException {
        try {
            if (!Files.isReadable(file)) {
                saveSymbols();
            } else {
                loadSymbols();
            }
        } catch(final EXistException e) {
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
        if(name.length() == 0) {
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
        if(ns == null || ns.length() == 0) {
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
            readEntry(is);
        }
    }

    private void readEntry(final VariableByteInput is) throws IOException {
        final byte type = is.readByte();
        final int id = is.readInt();
        final String key = is.readUTF();
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
        try(final VariableByteOutputStream os = new VariableByteOutputStream(8192);
                final OutputStream fos =  new BufferedOutputStream(Files.newOutputStream(getFile()))) {
            writeAll(os);
            fos.write(os.toByteArray());
        } catch(final FileNotFoundException e) {
            throw new EXistException("File not found: " + this.getFile().toAbsolutePath(), e);
        } catch(final IOException e) {
            throw new EXistException("IO error occurred while creating "
                + this.getFile().toAbsolutePath(), e);
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

    private OutputStream getOutputStream() throws IOException {
        if(os == null) {
            os = new BufferedOutputStream(Files.newOutputStream(getFile(), StandardOpenOption.APPEND));
        }
        return os;
    }

    @Override
    public void close() throws IOException {
        outBuffer.close();
        if(os != null) {
            os.close();
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
            return symbolsById[id];
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

            id = add(++offset, name);
            //we use "++offset" here instead of "offset++", 
            //because the system expects id's to start at 1, not 0
            write(id, name);
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
                getOutputStream().write(outBuffer.toByteArray());
                getOutputStream().flush();
            } catch(final FileNotFoundException e) {
                LOG.error("Symbol table: file not found!", e);
                //TODO :throw exception -pb
            } catch(final IOException e) {
                LOG.error("Symbol table: caught exception while writing!", e);
                //TODO : throw exception -pb
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
