package org.exist.dom;

import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.w3c.dom.*;
import java.io.*;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;

public class SymbolTable {

    protected TObjectIntHashMap symbols = new TObjectIntHashMap();
    protected TIntObjectHashMap names = new TIntObjectHashMap();
    protected short max = 0;
    protected short nextPartition = 0;
    protected boolean changed = false;

    public SymbolTable() {
    }

    public synchronized short getSymbol(Element element) {
        if (symbols.containsKey(element.getTagName()))
            return (short) symbols.get(element.getTagName());
        short id = ++max;
        symbols.put(element.getTagName(), id);
        names.put(id, element.getTagName());
	    changed = true;
        return id;
    }

    public synchronized short getSymbol(Attr attr) {
        if (symbols.containsKey("@" + attr.getName()))
            return (short) symbols.get("@" + attr.getName());
        short id = ++max;
        symbols.put("@" + attr.getName(), id);
        names.put(id, attr.getName());
	    changed = true;
        return id;
    }

    public synchronized short getSymbol(String name) {
        if(symbols.containsKey(name))
            return (short) symbols.get(name);
        short id = ++max;
        symbols.put(name, id);
        names.put(id, name);
        changed = true;
        return id;
    }
    
    public synchronized boolean hasChanged() {
	    return changed;
    }

    public synchronized String getName(short id) {
        return (String) names.get(id);
    }

    public short getNextIndexPartition() {
        return ++nextPartition;
    }
    
    public String[] getSymbols() {
        String[] result = new String[symbols.size()];
        Object[] keys = symbols.keys();
        for (int i = 0; i < keys.length; i++)
            result[i] = (String) keys[i];
        return result;
    }

    protected synchronized void write(final DataOutput ostream) throws IOException {
        ostream.writeShort( max );
        ostream.writeShort( nextPartition );
        ostream.writeInt(symbols.size());
        symbols.forEachEntry(new TObjectIntProcedure() {
            public boolean execute(Object key, int val) {
                try {
                    ostream.writeUTF((String) key);
                    ostream.writeShort((short) val);
                } catch (IOException ioe) {
                    return false;
                }
                return true;
            }
        });
        changed = false;
    }

    public synchronized void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeShort(max);
        ostream.writeShort(nextPartition);
        ostream.writeInt(symbols.size());
        System.out.println("symbols = " + symbols.size());
        symbols.forEachEntry(new TObjectIntProcedure() {
            public boolean execute(Object key, int val) {
                try {
                    ostream.writeUTF((String) key);
                    ostream.writeShort((short) val);
                } catch (IOException ioe) {
                    return false;
                }
                return true;
            }
        });
        changed = false;
    }

    protected void read(DataInput istream) throws IOException {
        max = istream.readShort();
        nextPartition = istream.readShort();
        int count = istream.readInt();
        String name;
        short id;
        for (int i = 0; i < count; i++) {
            name = istream.readUTF();
            id = istream.readShort();
            symbols.put(name, id);
            if (name.charAt(0) == '@')
                names.put(id, name.substring(1));
            else
                names.put(id, name);
        }
        changed = false;
    }

    public synchronized void read(VariableByteInputStream istream) throws IOException {
        max = istream.readShort();
        nextPartition = istream.readShort();
        int count = istream.readInt();
        String name;
        short id;
        for (int i = 0; i < count; i++) {
            name = istream.readUTF();
            id = istream.readShort();
            symbols.put(name, id);
            if (name.charAt(0) == '@')
                names.put(id, name.substring(1));
            else
                names.put(id, name);
        }
        changed = false;
    }
}
