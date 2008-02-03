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

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.ElementValue;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteInputStream;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.Configuration;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.Object2IntHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.*;
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

    public final static short FILE_FORMAT_VERSION_ID = 7;

    public static int LENGTH_LOCAL_NAME = 2; //sizeof short
	public static int LENGTH_NS_URI = 2; //sizeof short	

    /** Maps local node names to an integer id */
	protected Object2IntHashMap nameSymbols = new Object2IntHashMap(200);
    
    /** Maps int ids to local node names */
	protected Int2ObjectHashMap names = new Int2ObjectHashMap(200);
    
    /** Maps namespace URIs to an integer id */
	protected Object2IntHashMap nsSymbols = new Object2IntHashMap(200);
    
    /** Maps int ids to namespace URIs */
	protected Int2ObjectHashMap namespaces = new Int2ObjectHashMap(200);

    /**
     * Contains default prefix-to-namespace mappings. For convenience, eXist tracks
     * the first prefix-to-namespace mapping it finds in a document. If an undefined prefix
     * is found in a query, the query engine will first look up the prefix in this table before
     * throwing an error.
     */
	protected Object2IntHashMap defaultMappings = new Object2IntHashMap(200);

    /**
     * Temporary name pool to share QName instances during indexing.
     */
	protected QNamePool namePool = new QNamePool();

    protected Object2IntHashMap mimeTypeByName = new Object2IntHashMap(32);
    protected Int2ObjectHashMap mimeTypeById = new Int2ObjectHashMap(32);

    /** contains the next local name id to be used */
	protected short max = 0;
    
    /** contains the next namespace URI id to be used */
	protected short nsMax = 0;
    
    /** set to true if the symbol table needs to be saved */
	protected boolean changed = false;

    /** the underlying symbols.dbx file */
	protected File file;
	
	public SymbolTable(BrokerPool pool, Configuration config) 
		throws EXistException {
        String dataDir = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        file = new File(dataDir + File.separatorChar + getFileName());
		if (!file.canRead()) {
			saveSymbols();
		} else
			loadSymbols();
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
		names.put(id, element.getLocalName());
		changed = true;
		// remember the prefix=namespace mapping for querying
		String prefix = element.getPrefix();
		if (prefix != null
			&& prefix.length() > 0
			&& (!defaultMappings.containsKey(prefix))) {
			final short nsId = getNSSymbol(element.getNamespaceURI());
			defaultMappings.put(prefix, nsId);
		}
		return id;
	}

    /**
     * Return a unique id for the local node name of the specified attribute.
     * 
     * @param attr
     */
	public synchronized short getSymbol(Attr attr) {
		final String key = '@' + attr.getLocalName();
		short id = (short) nameSymbols.get(key);
		if (id != -1)
			return id;
		id = ++max;
		nameSymbols.put(key, id);
		names.put(id, attr.getLocalName());
		changed = true;
		//		remember the prefix=namespace mapping for querying
		String prefix = attr.getPrefix();
		if (prefix != null
			&& prefix.length() > 0
			&& (!defaultMappings.containsKey(prefix))) {
			final short nsId = getNSSymbol(attr.getNamespaceURI());
			defaultMappings.put(prefix, nsId);
		}
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
		names.put(id, name);
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
		namespaces.put(id, ns);
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
		return id == 0 ? "" : (String) namespaces.get(id);
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
		return (String) names.get(id);
	}

    /**
     * Returns a namespace URI for the given prefix if there's
     * a default mapping.
     * 
     * @param prefix
     */
	public synchronized String getDefaultNamespace(String prefix) {
		if (defaultMappings.containsKey(prefix))
			return getNamespace((short)defaultMappings.get(prefix));
		return null;
	}
	
    /**
     * Returns a list of default prefixes registered.
     * 
     */
	public synchronized String[] defaultPrefixList() {
		String[] prefixes = new String[defaultMappings.size()];
		int i = 0;
		for (Iterator j = defaultMappings.iterator(); j.hasNext(); i++)
			prefixes[i] = (String) j.next();
		return prefixes;
	}

    public synchronized int getMimeTypeId(String mimeType) {
        int id = mimeTypeByName.get(mimeType);
        if (id == -1) {
            int maxId = 0;
            for (Iterator i = mimeTypeById.iterator(); i.hasNext(); ) {
                Integer val = (Integer) i.next();
                maxId = Math.max(maxId, val.intValue());
            }
            id = ++maxId;
            mimeTypeByName.put(mimeType, id);
            mimeTypeById.put(id, mimeType);
            changed = true;
        }
        return id;
    }

    public synchronized String getMimeType(int id) {
        return (String) mimeTypeById.get(id);
    }

    /**
     * Write the symbol table to persistent storage.
     * 
     * @param ostream
     * @throws IOException
     */
	public synchronized void write(final VariableByteOutputStream ostream)
		throws IOException {
        ostream.writeFixedInt(FILE_FORMAT_VERSION_ID);
        ostream.writeShort(max);
		ostream.writeShort(nsMax);
		ostream.writeInt(nameSymbols.size());
		for (Iterator i = nameSymbols.iterator(); i.hasNext();) {
			final String entry = (String) i.next();
			ostream.writeUTF(entry);
			short id = (short) nameSymbols.get(entry);
			if(id < 0)
				Thread.dumpStack();
			ostream.writeShort(id);
		}
		ostream.writeInt(nsSymbols.size());
		for (Iterator i = nsSymbols.iterator(); i.hasNext();) {
			final String entry = (String) i.next();
			ostream.writeUTF(entry);
			short id = (short) nsSymbols.get(entry);
			if(id < 0)
				Thread.dumpStack();
			ostream.writeShort(id);
		}
		ostream.writeInt(defaultMappings.size());
		String prefix;
		short nsId;
		for (Iterator i = defaultMappings.iterator(); i.hasNext();) {
			prefix = (String) i.next();
			nsId = (short)defaultMappings.get(prefix);
			ostream.writeUTF(prefix);
			ostream.writeShort(nsId);
		}
        ostream.writeInt(mimeTypeByName.size());
        String mime;
        int mimeId;
        for (Iterator i = mimeTypeByName.iterator(); i.hasNext(); ) {
            mime = (String) i.next();
            mimeId = mimeTypeByName.get(mime);
            ostream.writeUTF(mime);
            ostream.writeInt(mimeId);
        }
        changed = false;
	}

    /**
     * Read the symbol table.
     * 
     * @param istream
     * @throws IOException
     */
	public synchronized void read(VariableByteInput istream) throws IOException {
        int magic = istream.readFixedInt();
        if (magic != FILE_FORMAT_VERSION_ID)
            throw new IOException("Database file symbols.dbx has a storage format incompatible with this " +
                "version of eXist. Please do a backup/restore of your data first.");
        max = istream.readShort();
		nsMax = istream.readShort();
		int count = istream.readInt();
		String name;
		short id;
		for (int i = 0; i < count; i++) {
			name = istream.readUTF();
			id = istream.readShort();
			nameSymbols.put(name, id);
			if (name.charAt(0) == '@')
				names.put(id, name.substring(1));
			else
				names.put(id, name);
		}
		count = istream.readInt();
		for (int i = 0; i < count; i++) {
			name = istream.readUTF();
			id = istream.readShort();
			nsSymbols.put(name, id);
			namespaces.put(id, name);
		}
		count = istream.readInt();
		String prefix;
		short nsId;
		for (int i = 0; i < count; i++) {
			prefix = istream.readUTF();
			nsId = istream.readShort();
			defaultMappings.put(prefix, nsId);
		}
        count = istream.readInt();
        String mime;
        int mimeId;
        for (int i = 0; i < count; i++) {
            mime = istream.readUTF();
            mimeId = istream.readInt();
            mimeTypeByName.put(mime, mimeId);
            mimeTypeById.put(mimeId, mime);
        }
        changed = false;
    }
	
	public File getFile() {
		return file;
	}
	
	/**
	 * Save the global symbol table. The global symbol table stores QNames and
	 * namespace/prefix mappings.
	 * 
	 * @throws EXistException
	 */
	public void saveSymbols() throws EXistException {
		synchronized (this) {
			try {
				VariableByteOutputStream os = new VariableByteOutputStream(256);
				this.write(os);
				FileOutputStream fos = new FileOutputStream(this.getFile()
						.getAbsolutePath(), false);
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
	}

	/**
	 * Read the global symbol table. The global symbol table stores QNames and
	 * namespace/prefix mappings.
	 * 
	 * @throws EXistException
	 */
	public void loadSymbols() throws EXistException {
		try {
			FileInputStream fis = new FileInputStream(this.getFile());
			VariableByteInput is = new VariableByteInputStream(fis);
			this.read(is);
			fis.close();
		} catch (FileNotFoundException e) {
			throw new EXistException("could not read "
					+ this.getFile().getAbsolutePath());
		} catch (IOException e) {
			throw new EXistException("io error occurred while reading "
					+ this.getFile().getAbsolutePath() + ": " + e.getMessage());
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
	    if (hasChanged())
	        saveSymbols();
	}	
}
