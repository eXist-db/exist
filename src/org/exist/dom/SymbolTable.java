/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.storage.BrokerPool;
import org.exist.storage.ElementValue;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.Configuration;
import org.exist.util.hashtable.Object2IntHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Iterator;

/**
 * Maintains a global symbol table shared by a database instance. The symbol
 * table maps namespace URIs and node names to unique, numeric ids. Internally,
 * the db does not store node QNames in clear text. Instead, it uses the numeric ids
 * maintained here.
 * 
 * The global SymbolTable singleton can be retrieved from {@link org.exist.storage.BrokerPool#getSymbols()}.
 * It is saved into the database file "symbols.dbx".
 * 
 * @author wolf
 * @author Adam Retter <adam@exist-db.org>
 *
 */
public class SymbolTable {

    private static final Logger LOG = Logger.getLogger(SymbolTable.class);

    private static final String FILE_NAME = "symbols.dbx";

    public final static short FILE_FORMAT_VERSION_ID = 8;
    public final static short LEGACY_FILE_FORMAT_VERSION_ID = 7;

    //TODO the bytes used by these types could be replaced by single bits in an EnumSet
    //if we can get bit level storage operations working
    public enum SymbolType {
        NAME((byte)0),
        NAMESPACE((byte)1),
        MIMETYPE((byte)2);

        private final byte type_id;

        private SymbolType(byte type_id) {
            this.type_id = type_id;
        }

        public final byte getTypeId() {
            return type_id;
        }

        public static SymbolType valueOf(byte type_id) {
            for(final SymbolType symbolType : SymbolType.values()) {
                if(symbolType.getTypeId() == type_id) {
                    return symbolType;
                }
            }
            throw new IllegalArgumentException("No such enumerated value for type_id:" + type_id);
        }
    }

    public static int LENGTH_LOCAL_NAME = 2; //sizeof short
    public static int LENGTH_NS_URI = 2; //sizeof short

    public final static char ATTR_NAME_PREFIX = '@';

    protected final SymbolCollection localNameSymbols = new LocalNameSymbolCollection(SymbolType.NAME, 200);
    protected final SymbolCollection namespaceSymbols = new SymbolCollection(SymbolType.NAMESPACE, 200);
    protected final SymbolCollection mimeTypeSymbols = new SymbolCollection(SymbolType.MIMETYPE, 32);

    /**
     * Temporary name pool to share QName instances during indexing.
     */
    private QNamePool namePool = new QNamePool();

    /** set to true if the symbol table needs to be saved */
    private boolean changed = false;

    /** the underlying symbols.dbx file */
    private final File file;
    private final VariableByteOutputStream outBuffer = new VariableByteOutputStream(512);
    private OutputStream os = null;

    public SymbolTable(BrokerPool pool, File dataDir) throws EXistException {
        file = new File(dataDir, getFileName());
        if(!file.canRead()) {
            saveSymbols();
        } else {
            loadSymbols();
        }
    }

    public SymbolTable(BrokerPool pool, Configuration config) throws EXistException {
        this(pool, new File((String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR)));
    }

    public static String getFileName() {
        return FILE_NAME;
    }

    /**
     * Retrieve a shared QName instance from the temporary pool.
     *
     * TODO: make the namePool thread-local to avoid synchronization.
     *
     * @param namespaceURI
     * @param localName
     * @param prefix
     */
    public synchronized QName getQName(short type, String namespaceURI, String localName, String prefix) {
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
     * @param element
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getSymbol(Element element) {
        return (short) localNameSymbols.getId(element.getLocalName());
    }

    /**
     * Return a unique id for the local node name of the specified attribute.
     * 
     * @param attr
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getSymbol(Attr attr) {
        final String key = ATTR_NAME_PREFIX + attr.getLocalName();
        return (short)localNameSymbols.getId(key);
    }

    /**
     * Returns a unique id for the specified local name. If the name is
     * the local name of an attribute, it should start with a '@' character.
     * 
     * @param name
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getSymbol(String name) {
        if(name.length() == 0) {
            throw new IllegalArgumentException("name is empty");
        }
        return (short)localNameSymbols.getId(name);
    }

    /**
     * Returns a unique id for the specified namespace URI.
     * 
     * @param ns
     */
    //TODO the (short) cast is nasty - should consider using either short or int end to end
    public synchronized short getNSSymbol(String ns) {
        if(ns == null || ns.length() == 0) {
            return 0;
        }
        return (short)namespaceSymbols.getId(ns);
    }

    public synchronized int getMimeTypeId(String mimeType) {
        return mimeTypeSymbols.getId(mimeType);
    }

    /**
     * Returns true if the symbol table needs to be saved
     * to persistent storage.
     * 
     */
    public synchronized boolean hasChanged() {
        return changed;
    }

    /**
     * Returns the local name registered for the id or
     * null if the name is not known.
     * 
     * @param id
     */
    public synchronized String getName(short id) {
        return localNameSymbols.getSymbol(id);
    }

    public synchronized String getMimeType(int id) {
        return mimeTypeSymbols.getSymbol(id);
    }

    /**
     * Returns the namespace URI registered for the id or null
     * if the namespace URI is not known. Returns the empty string
     * if the namespace is empty.
     *
     * @param id
     */
    public synchronized String getNamespace(short id) {
        return namespaceSymbols.getSymbol(id);
    }

    /**
     * Write the symbol table to persistent storage. Only called when upgrading
     * a .dbx file from previous versions.
     * 
     * @param os outputstream
     * @throws IOException
     */
    private synchronized void writeAll(VariableByteOutputStream os) throws IOException {
        os.writeFixedInt(FILE_FORMAT_VERSION_ID);
        localNameSymbols.write(os);
        namespaceSymbols.write(os);
        mimeTypeSymbols.write(os);
        changed = false;
    }

    /**
     * Read the symbol table from disk.
     *
     * @param is
     * @throws IOException
     */
    protected final void read(VariableByteInput is) throws IOException {
        localNameSymbols.clear();
        namespaceSymbols.clear();
        mimeTypeSymbols.clear();
        while(is.available() > 0) {
            readEntry(is);
        }
    }

    private void readEntry(VariableByteInput is) throws IOException {
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
     * @param istream
     * @throws IOException
     */
    protected final void readLegacy(VariableByteInput istream) throws IOException {
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

    public File getFile() {
        return file;
    }

    /**
     * Save the entire symbol table. Will only be called when initializing an
     * empty database or when upgrading an older dbx file.
     *
     * @throws EXistException
     */
    private void saveSymbols() throws EXistException {
        try {
            final VariableByteOutputStream os = new VariableByteOutputStream(256);
            writeAll(os);
            final FileOutputStream fos = new FileOutputStream(getFile().getAbsolutePath(), false);
            fos.write(os.toByteArray());
            fos.close();
        } catch(final FileNotFoundException e) {
            throw new EXistException("File not found: " + this.getFile().getAbsolutePath());
        } catch(final IOException e) {
            throw new EXistException("IO error occurred while creating "
                + this.getFile().getAbsolutePath());
        }
    }

    /**
     * Read the global symbol table. The global symbol table stores QNames and
     * namespace/prefix mappings.
     *
     * @throws EXistException
     */
    private synchronized void loadSymbols() throws EXistException {
        try {
            final FileInputStream fis = new FileInputStream(getFile());
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
            fis.close();
        } catch(final FileNotFoundException e) {
            throw new EXistException("Could not read " + this.getFile().getAbsolutePath());
        } catch(final IOException e) {
            throw new EXistException("IO error occurred while reading "
                + this.getFile().getAbsolutePath() + ": " + e.getMessage(), e);
        }
    }

    public void backupSymbolsTo(OutputStream os) throws IOException {
        final FileInputStream fis = new FileInputStream(this.getFile());
        final byte[] buf = new byte[1024];
        int len;
        while((len = fis.read(buf)) > 0) {
            os.write(buf, 0, len);
        }
        fis.close();
    }

    public void backupToArchive(RawDataBackup backup) throws IOException {
        final OutputStream os = backup.newEntry(getFile().getName());
        backupSymbolsTo(os);
    }

    public void flush() throws EXistException {
        //Noting to do ? -pb
    }

    private OutputStream getOutputStream() throws FileNotFoundException {
        if (os == null) {
            os = new FileOutputStream(getFile().getAbsolutePath(), true);
        }
        return os;
    }

    public void close() throws IOException {
        if (os != null) {
            os.close();
        }
    }

    /**
     * Represents a distinct collection of symbols
     *
     * @author wolf
     * @author Adam Retter <adam@exist-db.org>
     */
    protected class SymbolCollection {

        private final SymbolType symbolType;

        /** Maps mimetype names to an integer id (persisted to disk) */
        private final Object2IntHashMap<String> symbolsByName;

        /** Maps int ids to mimetype names (transient map for fast reverse lookup of symbolsByName) */
        private String[] symbolsById;

        /** contains the offset of the last symbol */
        protected short offset = 0;

        public SymbolCollection(SymbolType symbolType, int initialSize) {
             this.symbolType = symbolType;
             symbolsByName = new Object2IntHashMap<String>(initialSize);
             symbolsById = new String[initialSize];
        }

        private SymbolType getSymbolType() {
            return symbolType;
        }

        private int add(int id, String name) {
            symbolsById = ensureCapacity(symbolsById, id);
            addSymbolById(id, name);
            addSymbolByName(name, id);
            if(id > offset) {
                offset = (short) id;
            }
            return id;
        }

        protected void addSymbolById(int id, String name) {
            symbolsById[id] = name;
        }

        protected void addSymbolByName(String name, int id) {
            symbolsByName.put(name, id);
        }

        /* Apparently unused. Commented out -pb
        private void ensureCapacity() {
            if(offset == symbolsById.length) {
                String[] newSymbolsById = new String[(offset * 3) / 2];
                System.arraycopy(symbolsById, 0, newSymbolsById, 0, offset);
                symbolsById = newSymbolsById;
            }
        }
        */

        protected String[] ensureCapacity(String[] array, int max) {
            if (array.length <= max) {
                final String[] newArray = new String[(max * 3) / 2];
                System.arraycopy(array, 0, newArray, 0, array.length);
                return newArray;
            }
            return array;
        }

        private void clear() {
            offset = 0;
        }

        public synchronized String getSymbol(int id) {
            if (id <= 0 || id > offset) {
                return ""; //TODO : raise an exception ? -pb
            }
            return symbolsById[id];
        }

        public synchronized int getId(String name) {
            int id = symbolsByName.get(name);
            if (id != -1) {
                return id;
            }
            // symbol space exceeded. return -1 to indicate.
            if (offset == Short.MAX_VALUE) {
                return -1;
            }

            id = add(++offset, name);
            //we use "++offset" here instead of "offset++", 
            //because the system expects id's to start at 1, not 0
            write(id, name);
            changed = true;
            return id;
        }

        protected final void write(VariableByteOutputStream os) throws IOException {
            String symbol;
            int id;
            for(final Iterator<String> i = symbolsByName.iterator(); i.hasNext();) {
                symbol = i.next();
                id = symbolsByName.get(symbol);
                if (id < 0) {
                    LOG.error("Symbol Table: symbolTypeId=" + getSymbolType() +
                        ", symbol='" + symbol + "', id=" + id);
                    //TODO : raise exception ? -pb
                }
                writeEntry(id, symbol, os);
            }
        }

        /**
         * Append a new entry to the .dbx file
         *
         * @param id
         * @param key
         */
        private void write(int id, String key) {
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

        private void writeEntry(int id, String key, VariableByteOutputStream os) throws IOException {
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
     * @author Adam Retter <adam@exist-db.org>
     */
    private class LocalNameSymbolCollection extends SymbolCollection {

        public LocalNameSymbolCollection(SymbolType symbolType, int initialSize) {
            super(symbolType, initialSize);
        }

        @Override
        protected void addSymbolById(int id, String name) {
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