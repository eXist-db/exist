/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import it.unimi.dsi.fastutil.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.Object2IntMap;
import it.unimi.dsi.fastutil.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.Object2ShortOpenHashMap;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class SymbolTable {

	private final static Logger LOG = Logger.getLogger(SymbolTable.class);

	protected Object2IntOpenHashMap nameSymbols = new Object2IntOpenHashMap();
	protected Int2ObjectOpenHashMap names = new Int2ObjectOpenHashMap();
	protected Object2IntOpenHashMap nsSymbols = new Object2IntOpenHashMap();
	protected Int2ObjectOpenHashMap namespaces = new Int2ObjectOpenHashMap();

	protected Object2ShortOpenHashMap defaultMappings = new Object2ShortOpenHashMap();

	protected short max = 0;
	protected short nsMax = 0;
	protected boolean changed = false;

	public SymbolTable() {
	}

	public synchronized short getSymbol(Element element) {
		if (nameSymbols.containsKey(element.getLocalName()))
			return (short) nameSymbols.getInt(element.getLocalName());
		short id = ++max;
		nameSymbols.put(element.getLocalName(), id);
		names.put(id, element.getLocalName());
		changed = true;
		// remember the prefix=namespace mapping for querying
		String prefix = element.getPrefix();
		if (prefix != null && prefix.length() > 0 && (!defaultMappings.containsKey(prefix))) {
			final short nsId = getNSSymbol(element.getNamespaceURI());
			defaultMappings.put(prefix, nsId);
		}
		return id;
	}

	public synchronized short getSymbol(Attr attr) {
		final String key = '@' + attr.getLocalName();
		if (nameSymbols.containsKey(key))
			return (short) nameSymbols.getInt(key);
		short id = ++max;
		nameSymbols.put(key, id);
		names.put(id, attr.getLocalName());
		changed = true;
		//		remember the prefix=namespace mapping for querying
		String prefix = attr.getPrefix();
		if (prefix != null && prefix.length() > 0 && (!defaultMappings.containsKey(prefix))) {
			final short nsId = getNSSymbol(attr.getNamespaceURI());
			defaultMappings.put(prefix, nsId);
		}
		return id;
	}

	public synchronized short getSymbol(String name) {
		if(name.length() == 0)
			throw new IllegalArgumentException("name is empty");
		if (nameSymbols.containsKey(name))
			return (short) nameSymbols.getInt(name);
		short id = ++max;
		nameSymbols.put(name, id);
		names.put(id, name);
		changed = true;
		return id;
	}

	public synchronized short getNSSymbol(String ns) {
		if (ns == null || ns.length() == 0) {
			return 0;
		}
		if (nsSymbols.containsKey(ns))
			return (short) nsSymbols.getInt(ns);
		short id = ++nsMax;
		nsSymbols.put(ns, id);
		namespaces.put(id, ns);
		changed = true;
		return id;
	}

	public synchronized String getNamespace(short id) {
		return id == 0 ? "" : (String) namespaces.get(id);
	}

	public synchronized boolean hasChanged() {
		return changed;
	}

	public synchronized String getName(short id) {
		return (String) names.get(id);
	}

	public String[] getSymbols() {
		String[] result = new String[nameSymbols.size()];
		Object[] keys = nameSymbols.keySet().toArray();
		for (int i = 0; i < keys.length; i++)
			result[i] = (String) keys[i];
		return result;
	}

	public synchronized String getDefaultNamespace(String prefix) {
		if (defaultMappings.containsKey(prefix))
			return getNamespace(defaultMappings.getShort(prefix));
		return null;
	}

	public synchronized String[] defaultPrefixList() {
		String[] prefixes = new String[defaultMappings.size()];
		int i = 0;
		for(Iterator j = defaultMappings.keySet().iterator(); j.hasNext(); i++)
			prefixes[i] = (String)j.next();
		return prefixes;
	}
	
	public synchronized void write(final VariableByteOutputStream ostream) throws IOException {
		ostream.writeShort(max);
		ostream.writeShort(nsMax);
		ostream.writeInt(nameSymbols.size());
		for (Iterator i = nameSymbols.entrySet().iterator(); i.hasNext();) {
			final Object2IntMap.Entry entry = (Object2IntMap.Entry) i.next();
			ostream.writeUTF((String) entry.getKey());
			ostream.writeShort((short) entry.getIntValue());
		}
		ostream.writeInt(nsSymbols.size());
		for (Iterator i = nsSymbols.entrySet().iterator(); i.hasNext();) {
			final Object2IntMap.Entry entry = (Object2IntMap.Entry) i.next();
			ostream.writeUTF((String) entry.getKey());
			ostream.writeShort((short) entry.getIntValue());
		}
		ostream.writeInt(defaultMappings.size());
		String prefix;
		short nsId;
		for (Iterator i = defaultMappings.keySet().iterator(); i.hasNext();) {
			prefix = (String) i.next();
			nsId = defaultMappings.getShort(prefix);
			ostream.writeUTF(prefix);
			ostream.writeShort(nsId);
		}
		changed = false;
	}

	public synchronized void read(VariableByteInputStream istream) throws IOException {
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
		changed = false;
	}
}
