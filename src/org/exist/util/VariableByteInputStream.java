/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id:
 */
package org.exist.util;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    21. September 2002
 */
public class VariableByteInputStream {

	private InputStream is_ = null;

	public VariableByteInputStream() {
	}

	/**
	 *  Constructor for the VariableByteInputStream object
	 *
	 *@param  data  Description of the Parameter
	 */
	public VariableByteInputStream(byte[] data) {
		is_ = new ByteArrayInputStream(data);
	}

	public VariableByteInputStream(byte[] data, int offset, int length) {
		is_ = new ByteArrayInputStream(data, offset, length);
	}

	public VariableByteInputStream(InputStream stream) {
		this.is_ = stream;
	}

	public void setInputStream(InputStream stream) {
		is_ = stream;
	}

	public void read(byte[] data, int offset, int len) throws IOException {
		is_.read(data, offset, len);
	}

	public byte readByte() throws IOException {
		return (byte) is_.read();
	}

	public short readShort() throws IOException, EOFException {
		byte b = (byte) is_.read();
		short i = (short) (b & 0177);
		int next;
		for (int shift = 7;(b & 0200) != 0; shift += 7) {
			next = is_.read();
			if (next < 0)
				throw new EOFException();
			b = (byte) next;
			i |= (b & 0177L) << shift;
		}
		return i;
	}

	public int readInt() throws EOFException, IOException {
		byte b = (byte) is_.read();
		int i = b & 0177;
		int next;
		for (int shift = 7;(b & 0200) != 0; shift += 7) {
			next = is_.read();
			if (next < 0)
				throw new EOFException();
			b = (byte) next;
			i |= (b & 0177L) << shift;
		}
		return i;
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public long readLong() throws EOFException, IOException {
		byte b = (byte) is_.read();
		long i = b & 0177;
		int next;
		for (int shift = 7;(b & 0200) != 0; shift += 7) {
			next = is_.read();
			if (next < 0)
				throw new EOFException();
			b = (byte) next;
			i |= (b & 0177L) << shift;
		}
		return i;
	}

	public long readFixedLong() throws IOException {
		return ((((long) is_.read()) & 0xffL) << 56)
			| ((((long) is_.read()) & 0xffL) << 48)
			| ((((long) is_.read()) & 0xffL) << 40)
			| ((((long) is_.read()) & 0xffL) << 32)
			| ((((long) is_.read()) & 0xffL) << 24)
			| ((((long) is_.read()) & 0xffL) << 16)
			| ((((long) is_.read()) & 0xffL) << 8)
			| (((long) is_.read()) & 0xffL);
	}

	public String readUTF() throws IOException, EOFException {
		int len = readInt();
		byte data[] = new byte[len];
		is_.read(data);
		String s;
		try {
			s = new String(data, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			s = new String(data);
		}
		return s;
	}

	public void skip(int count) throws IOException {
		for (int i = 0; i < count && is_.available() > 0; i++) {
			while ((is_.read() & 0200) > 0);
		}
	}

	public int available() throws IOException {
		return is_.available();
	}

	public void copyTo(VariableByteOutputStream os) throws IOException {
		int more;
		do {
			more = is_.read();
			os.buf.append((byte) more);
			more &= 0200;
		} while (more > 0);
	}

	public void copyTo(VariableByteOutputStream os, int count)
		throws IOException {
		int more;
		for (int i = 0; i < count; i++) {
			do {
				more = is_.read();
				os.buf.append((byte) more);
				more &= 0200;
			} while (more > 0);
		}
	}
}
