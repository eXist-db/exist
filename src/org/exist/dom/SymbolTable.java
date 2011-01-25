/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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

import org.apache.log4j.Logger;
import org.eclipse.jetty.util.log.Log;
import org.exist.EXistException;
import org.exist.management.impl.SanityReport;
import org.exist.storage.BrokerPool;
import org.exist.storage.ElementValue;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.Configuration;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.Object2IntHashMap;
import org.exist.util.sanity.SanityCheck;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.*;
import java.nio.ByteBuffer;
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
 *
 */
public class SymbolTable {
	
    public static final String FILE_NAME = "symbols.dbx";

    public final static short FILE_FORMAT_VERSION_ID = 8;
    public final static short LEGACY_FILE_FORMAT_VERSION_ID = 7;

    public final static byte NAME_ID_TYPE = 0;
    public final static byte NAMESPACE_ID_TYPE = 1;
    public final static byte MIME_ID_TYPE = 2;
    
	private static final Logger LOG = Logger.getLogger(SymbolTable.class);
    
    public static int LENGTH_LOCAL_NAME = 2; //sizeof short
	public static int LENGTH_NS_URI = 2; //sizeof short	

    /** Maps local node names to an integer id */
	protected Object2IntHashMap<String> nameSymbols = new Object2IntHashMap<String>(200);
    
    /** Maps int ids to local node names */
	protected String[] names = new String[200];
    
    /** Maps namespace URIs to an integer id */
	protected Object2IntHashMap<String> nsSymbols = new Object2IntHashMap<String>(200);
    
    /** Maps int ids to namespace URIs */
	protected String[] namespaces = new String[200];
    /**
     * Temporary name pool to share QName instances during indexing.
     */
	protected QNamePool namePool = new QNamePool();

    protected Object2IntHashMap<String> mimeTypeByName = new Object2IntHashMap<String>(32);
    protected String[] mimeTypeById = new String[32];

    /** contains the next mime type id to be used */
    protected short maxMime = 0;
    
    /** contains the next local name id to be used */
	protected short max = 0;
    
    /** contains the next namespace URI id to be used */
	protected short nsMax = 0;
    
    /** set to true if the symbol table needs to be saved */
	protected boolean changed = false;

    /** the underlying symbols.dbx file */
	protected File file;
	
	protected VariableByteOutputStream outBuffer = new VariableByteOutputStream(512);
	protected OutputStream outStream = null;
	
	public SymbolTable(BrokerPool pool, File dataDir) throws EXistException {
	    file = new File(dataDir, getFileName());
		if (!file.canRead()) {
			saveSymbols();
		} else
			loadSymbols();
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
        byte itype = type == Node.ATTRIBUTE_NODE ? ElementValue.ATTRIBUTE : ElementValue.ELEMENT;
        QName qn = namePool.get(itype, namespaceURI, localName, prefix);
        if (qn == null) {
            qn = namePool.add(itype, namespaceURI, localName, prefix);
        }
        return qn;
    }
	
    /**
     * Return a unique id for the local node name of the specified element.
     * 
     * @param element
     */
	public synchronized short getSymbol(Element element) {
		short id = (short) nameSymbols.get(element.getLocalName());
		if (id != -1)
			return id;
		id = ++max;
		nameSymbols.put(element.getLocalName(), id);
		
		ensureCapacity();
		names[id] = element.getLocalName();

		write(NAME_ID_TYPE, id, names[id]);
		
		changed = true;
		return id;
	}

    /**
     * Return a unique id for the local node name of the specified attribute.
     * 
     * @param attr
     */
	public synchronized short getSymbol(Attr attr) {
		String localName = attr.getLocalName();
		final String key = '@' + localName;
		short id = (short) nameSymbols.get(key);
		if (id != -1)
			return id;
		id = ++max;
		nameSymbols.put(key, id);
		
		ensureCapacity();
		names[id] = localName;

		write(NAME_ID_TYPE, id, localName);
		changed = true;
		return id;
	}

    /**
     * Returns a unique id for the specified local name. If the name is
     * the local name of an attribute, it should start with a '@' character.
     * 
     * @param name
     */
	public synchronized short getSymbol(String name) {
		if (name.length() == 0)
			throw new IllegalArgumentException("name is empty");
		short id = (short) nameSymbols.get(name);
		if (id != -1)
			return id;
		id = ++max;
		nameSymbols.put(name, id);
		
		ensureCapacity();
		names[id] = name;
		
		write(NAME_ID_TYPE, id, name);
		changed = true;
		return id;
	}

    /**
     * Returns a unique id for the specified namespace URI.
     * 
     * @param ns
     */
	public synchronized short getNSSymbol(String ns) {
		if (ns == null || ns.length() == 0) {
			return 0;
		}
		short id = (short) nsSymbols.get(ns);
		if (id != -1)
			return id;
		id = ++nsMax;
		nsSymbols.put(ns, id);
		
		ensureCapacity();
		namespaces[id] = ns;
		
		write(NAMESPACE_ID_TYPE, id, ns);
		
		changed = true;
		return id;
	}

    /**
     * Returns the namespace URI registered for the id or null
     * if the namespace URI is not known. Returns the empty string
     * if the namespace is empty.
     * 
     * @param id
     */
	public synchronized String getNamespace(short id) {
		if (id <= 0 || id > nsMax)
			return "";
		return namespaces[id];
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
		if (id <= 0 || id > max)
			return "";
		return names[id];
	}

    public synchronized int getMimeTypeId(String mimeType) {
        int id = mimeTypeByName.get(mimeType);
        if (id == -1) {
            id = ++maxMime;
            ensureCapacity();
            mimeTypeByName.put(mimeType, id);
            mimeTypeById[id] = mimeType;
            
            write(MIME_ID_TYPE, id, mimeType);
            changed = true;
        }
        return id;
    }

    public synchronized String getMimeType(int id) {
    	if (id < 0 || id >= maxMime)
    		return "";
        return mimeTypeById[id];
    }

    /**
     * Append a new entry to the .dbx file
     * 
     * @param type
     * @param id
     * @param key
     */
    protected void write(byte type, int id, String key) {
    	outBuffer.clear();
    	try {
			writeEntry(type, id, key, outBuffer);
			if (outStream == null)
				outStream = new FileOutputStream(this.getFile().getAbsolutePath(), true);
			outStream.write(outBuffer.toByteArray());
			outStream.flush();
		} catch (FileNotFoundException e) {
			LOG.error("Symbol table: file not found!", e);
		} catch (IOException e) {
			LOG.error("Symbol table: caught exception while writing!", e);
		}
    }
    
    /**
     * Write the symbol table to persistent storage. Only called when upgrading
     * a .dbx file from previous versions.
     * 
     * @param ostream
     * @throws IOException
     */
	protected synchronized void writeAll(VariableByteOutputStream os)
		throws IOException {
		os.writeFixedInt(FILE_FORMAT_VERSION_ID);
		
		for (Iterator<String> i = nameSymbols.iterator(); i.hasNext();) {
			final String entry = i.next();
			short id = (short) nameSymbols.get(entry);
			if(id < 0)
				LOG.error("symbol table: name id for " + entry + " < 0");
			writeEntry(NAME_ID_TYPE, id, entry, os);
		}

		for (Iterator<String> i = nsSymbols.iterator(); i.hasNext();) {
			final String entry = i.next();
			short id = (short) nsSymbols.get(entry);
			if(id < 0)
				LOG.error("symbol table: namespace id for " + entry + " < 0");
			writeEntry(NAMESPACE_ID_TYPE, id, entry, os);
		}

        String mime;
        int mimeId;
        for (Iterator<String> i = mimeTypeByName.iterator(); i.hasNext(); ) {
            mime = i.next();
            mimeId = mimeTypeByName.get(mime);
            writeEntry(MIME_ID_TYPE, mimeId, mime, os);
        }
        changed = false;
	}

	protected void writeEntry(byte type, int id, String key, VariableByteOutputStream os) throws IOException {
		os.writeByte(type);
		os.writeInt(id);
		os.writeUTF(key);
	}
	
	/**
	 * Read the symbol table from disk.
	 * 
	 * @param is
	 * @throws IOException
	 */
	protected void read(VariableByteInput is) throws IOException {
		max = 0;
		nsMax = 0;
		maxMime = 0;
		while (is.available() > 0) {
			byte type = is.readByte();
			int id = is.readInt();
			String key = is.readUTF();
			switch (type) {
				case NAME_ID_TYPE:
					names = ensureCapacity(names, id);
					if (key.charAt(0) == '@')
						names[id] = key.substring(1);
					else
						names[id] = key;
					nameSymbols.put(key, id);
					if (id > max) max = (short) id;
					break;
				case NAMESPACE_ID_TYPE:
					namespaces = ensureCapacity(namespaces, id);
					namespaces[id] = key;
					nsSymbols.put(key, id);
					if (id > nsMax) nsMax = (short) id;
					break;
				case MIME_ID_TYPE:
					mimeTypeById = ensureCapacity(mimeTypeById, id);
					mimeTypeById[id] = key;
					mimeTypeByName.put(key, id);
					if (id > maxMime) maxMime = (short) id;
					break;
			}
		}
	}
	
    /**
     * Legacy method: read a symbol table written by a previous eXist version.
     * 
     * @param istream
     * @throws IOException
     */
	protected void readLegacy(VariableByteInput istream) throws IOException {
        max = istream.readShort();
		nsMax = istream.readShort();
		
		names = new String[(max * 3) / 2];
		namespaces = new String[(nsMax * 3) / 2];

		int count = istream.readInt();
		
		String name;
		short id;
		for (int i = 0; i < count; i++) {
			name = istream.readUTF();
			id = istream.readShort();
			nameSymbols.put(name, id);
			if (name.charAt(0) == '@')
				names[id] = name.substring(1);
			else
				names[id] = name;
		}
		count = istream.readInt();
		for (int i = 0; i < count; i++) {
			name = istream.readUTF();
			id = istream.readShort();
			nsSymbols.put(name, id);
			namespaces[id] = name;
		}
		
		// default mappings have been removed
		// read them for backwards compatibility
		count = istream.readInt();
		for (int i = 0; i < count; i++) {
			istream.readUTF();
			istream.readShort();
		}

		count = istream.readInt();
		maxMime = (short) count;
		mimeTypeById = new String[(maxMime * 3) / 2];
        String mime;
        int mimeId;
        for (int i = 0; i < count; i++) {
            mime = istream.readUTF();
            mimeId = istream.readInt();
            mimeTypeByName.put(mime, mimeId);
            mimeTypeById[mimeId] = mime;
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
	protected void saveSymbols() throws EXistException {
		try {
			VariableByteOutputStream os = new VariableByteOutputStream(256);
			writeAll(os);
			FileOutputStream fos = new FileOutputStream(getFile().getAbsolutePath(), false);
			fos.write(os.toByteArray());
			fos.close();
		} catch (FileNotFoundException e) {
			throw new EXistException("file not found: "
					+ this.getFile().getAbsolutePath());
		} catch (IOException e) {
			throw new EXistException("io error occurred while creating "
					+ this.getFile().getAbsolutePath());
		}
	}

	/**
	 * Read the global symbol table. The global symbol table stores QNames and
	 * namespace/prefix mappings.
	 * 
	 * @throws EXistException
	 */
	protected synchronized void loadSymbols() throws EXistException {
		try {
			FileInputStream fis = new FileInputStream(this.getFile());
			VariableByteInput is = new VariableByteInputStream(fis);
			
			int magic = is.readFixedInt();
			if (magic == LEGACY_FILE_FORMAT_VERSION_ID) {
				LOG.info("Converting legacy symbols.dbx to new format...");
				readLegacy(is);
				saveSymbols();
			} else if (magic != FILE_FORMAT_VERSION_ID)
				throw new EXistException("Symbol table was created by an older or newer version of eXist" +
						" (file id: " + magic + "). " +
						"To avoid damage, the database will stop.");
			else
				read(is);
			fis.close();
		} catch (FileNotFoundException e) {
			throw new EXistException("could not read "
					+ this.getFile().getAbsolutePath());
		} catch (IOException e) {
			throw new EXistException("io error occurred while reading "
					+ this.getFile().getAbsolutePath() + ": " + e.getMessage(), e);
		}
	}

	public void backupSymbolsTo(OutputStream os) throws IOException {
		FileInputStream fis = new FileInputStream(this.getFile());
		byte[] buf = new byte[1024];
		int len;
		while ((len = fis.read(buf)) > 0) {
			os.write(buf, 0, len);
		}
		fis.close();
	}

	public void flush() throws EXistException {
	}
	
	public void close() throws IOException {
		if (outStream != null)
			outStream.close();
	}
	
	private void ensureCapacity() {
		if (max == names.length) {
			String[] newNames = new String[(max * 3) / 2];
			System.arraycopy(names, 0, newNames, 0, max);
			names = newNames;
		}
		if (nsMax == namespaces.length) {
			String[] newNamespaces = new String[(nsMax * 3) / 2];
			System.arraycopy(namespaces, 0, newNamespaces, 0, nsMax);
			namespaces = newNamespaces;
		}
		if (maxMime == mimeTypeById.length) {
			String[] newMime = new String[(maxMime * 3) / 2];
			System.arraycopy(mimeTypeById, 0, newMime, 0, maxMime);
			mimeTypeById = newMime;
		}
	}
	
	private String[] ensureCapacity(String[] array, int max) {
		if (array.length <= max) {
			String[] newArray = new String[(max * 3) / 2];
			System.arraycopy(array, 0, newArray, 0, array.length);
			return newArray;
		}
		return array;
	}
}
