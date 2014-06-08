/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.util.hashtable.Object2IntHashMap;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SymbolTable {
    
    public final static short FILE_FORMAT_VERSION_ID = 1;

    private final File file;
    private DataOutputStream os = null;
    
    public SymbolTable(int initialSize, File file) throws EXistException {
        symbolsByName = new Object2IntHashMap<String>(initialSize);
        symbolsById = new String[initialSize];

        this.file = file;
        if(file.canRead()) {
            loadSymbols();
        }

        saveSymbols();
    }
    
    public String getIdtoHexString(String name) throws IOException {
        return Integer.toHexString( getId(name) );
    }

    public int getId(String name) throws IOException {
        if(name == null) {
            throw new IOException("name is NULL");
        }
        if(name.length() == 0) {
            throw new IOException("name is empty");
        }
        return _getId(name);
    }
    
    protected synchronized void close() throws IOException {
        if (os != null) {
            os.close();
            os = null;
        }
    }
    
    private synchronized void saveSymbols() throws EXistException {

        try {
            close();
        } catch (IOException e) {
            throw new EXistException("IO error occurred while closing " + file.getAbsolutePath());
        }
        
        os = getOutputStream();
        try {
            //magic
            os.writeShort(FILE_FORMAT_VERSION_ID);
            //size
            os.writeInt(symbolsByName.size());
            
            //write entries
            String symbol = null;
            int id = -1;
            for(final Iterator<String> i = symbolsByName.iterator(); i.hasNext();) {
                symbol = i.next();
                id = symbolsByName.get(symbol);
                if (id < 0) {
                    //LOG.error("Symbol Table: symbolTypeId=" + getSymbolType() + ", symbol='" + symbol + "', id=" + id);
                }

                writeEntry(id, symbol);
            }
            
            os.flush();

        } catch(final IOException e) {
            throw new EXistException("IO error occurred while creating " + file.getAbsolutePath());
        }
    }
    
    private synchronized void loadSymbols() throws EXistException {

        try {
            close();
        } catch (IOException e) {
            throw new EXistException("IO error occurred while closing " + file.getAbsolutePath());
        }
        
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
        } catch(final FileNotFoundException e) {
            throw new EXistException("File not found: " + file.getAbsolutePath());
        }
        
        clear();
        
        try {
            BufferedInputStream bis = new BufferedInputStream(fis);
            DataInputStream in = new DataInputStream(bis);
            
            int magic = in.readShort();
            if(magic != FILE_FORMAT_VERSION_ID) {
                throw new EXistException("Symbol table was created by an older" +
                    "or newer version of eXist" + " (file id: " + magic + "). " +
                    "To avoid damage, the database will stop.");
            }
            
            int size = in.readInt();
            try {
                for (int x = 0; x < size; x++) {
                    int key = in.readInt();
                    String value = in.readUTF();
                    
                    add(key, value);
                }
            } catch (EOFException e) {
                
            }
    
        } catch(final IOException e) {
            throw new EXistException("IO error occurred while reading " + file.getAbsolutePath());
        } finally {
            try {
                fis.close();
            } catch (IOException e) {
                throw new EXistException("IO error occurred while closing " + file.getAbsolutePath());
            }
        }
    }
    
    private DataOutputStream getOutputStream() throws EXistException {
        if (os == null) {
            FileOutputStream fos;
            try {
                fos = new FileOutputStream(file, false);
            } catch(final FileNotFoundException e) {
                throw new EXistException("File not found: " + file.getAbsolutePath());
            }
            
            os = new DataOutputStream(new BufferedOutputStream(fos));
        }
        return os;
    }

    /** Maps mimetype names to an integer id (persisted to disk) */
    private final Object2IntHashMap<String> symbolsByName;

    /** Maps int ids to mimetype names (transient map for fast reverse lookup of symbolsByName) */
    private String[] symbolsById;

    /** contains the offset of the last symbol */
    protected int offset = 0;

    private int add(int id, String name) {
        symbolsById = ensureCapacity(symbolsById, id);

        symbolsById[id] = name;
        symbolsByName.put(name, id);

        if(id > offset) {
            offset = id;
        }
        return id;
    }

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

    public String getSymbolFromHexString(String id) {
        return getSymbol( Integer.parseInt(id, 16) );
    }

    public synchronized String getSymbol(int id) {
        if (id <= 0 || id > offset) {
            return null;
        }
        return symbolsById[id];
    }

    private synchronized int _getId(String name) throws IOException {
        int id = symbolsByName.get(name);
        if (id != -1) {
            return id;
        }
        //we use "++offset" here instead of "offset++", because the system expects id's to start at 1, not 0
        id = add(++offset, name); 
        
        write(id, name);

        return id;
    }

    /**
     * Append a new entry to the .dbx file
     *
     * @param id
     * @param key
     * @throws IOException 
     */
    private void write(int id, String key) throws IOException {
        writeEntry(id, key);
        os.flush();
    }
    
    private void writeEntry(int id, String key) throws IOException {
        os.writeInt(id);
        os.writeUTF(key);
    }

}
