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

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.util.hashtable.Object2IntHashMap;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

public class SymbolTable {

	private final static Logger LOG = Logger.getLogger(SymbolTable.class);

	protected Object2IntHashMap nameSymbols = new Object2IntHashMap(200);
	protected Int2ObjectHashMap names = new Int2ObjectHashMap(200);
	protected Object2IntHashMap nsSymbols = new Object2IntHashMap(200);
	protected Int2ObjectHashMap namespaces = new Int2ObjectHashMap(200);

	protected Object2IntHashMap defaultMappings = new Object2IntHashMap(200);

	protected QNamePool namePool = new QNamePool();
	
	protected short max = 0;
	protected short nsMax = 0;
	protected boolean changed = false;

	protected File file;
	
	public SymbolTable(File file) {
		this.file = file;
	}

	public synchronized QName getQName(String namespaceURI, String localName, String prefix) {
	    return namePool.add(namespaceURI, localName, prefix);
	}
	
	public synchronized short getSymbol(Element element) {
		short id = (short) nameSymbols.get(element.getLocalName());
		if (id > -1)
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

	public synchronized short getSymbol(Attr attr) {
		final String key = '@' + attr.getLocalName();
		short id = (short) nameSymbols.get(key);
		if (id > -1)
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

	public synchronized short getSymbol(String name) {
		if (name.length() == 0)
			throw new IllegalArgumentException("name is empty");
		short id = (short) nameSymbols.get(name);
		if (id > -1)
			return id;
		id = ++max;
		nameSymbols.put(name, id);
		names.put(id, name);
		changed = true;
		return id;
	}

	public synchronized short getNSSymbol(String ns) {
		if (ns == null || ns.length() == 0) {
			return 0;
		}
		short id = (short) nsSymbols.get(ns);
		if (id > -1)
			return id;
		id = ++nsMax;
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
		int j = 0;
		for (Iterator i = nameSymbols.iterator(); i.hasNext(); j++) {
			result[j] = (String) i.next();
		}
		return result;
	}

	public synchronized String getDefaultNamespace(String prefix) {
		if (defaultMappings.containsKey(prefix))
			return getNamespace((short)defaultMappings.get(prefix));
		return null;
	}
	
	public synchronized String[] defaultPrefixList() {
		String[] prefixes = new String[defaultMappings.size()];
		int i = 0;
		for (Iterator j = defaultMappings.iterator(); j.hasNext(); i++)
			prefixes[i] = (String) j.next();
		return prefixes;
	}

	public synchronized void write(final VariableByteOutputStream ostream)
		throws IOException {
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
		changed = false;
	}

	public synchronized void read(VariableByteInput istream) throws IOException {
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
	
	public File getFile() {
		return file;
	}
}
