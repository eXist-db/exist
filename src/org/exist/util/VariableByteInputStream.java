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
import java.io.UnsupportedEncodingException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    21. September 2002
 */
public class VariableByteInputStream extends ByteArrayInputStream {

	/**
	 *  Constructor for the VariableByteInputStream object
	 *
	 *@param  data  Description of the Parameter
	 */
	public VariableByteInputStream(byte[] data) {
		super(data);
	}

	public VariableByteInputStream(byte[] data, int offset, int length) {
		super(data, offset, length);
	}

	public byte readByte() {
		return (byte) read();
	}

	public short readShort() throws EOFException {
		try {
			return (short) VariableByteCoding.decode(this);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EOFException();
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public int readInt() throws EOFException {
		try {
			return (int) VariableByteCoding.decode(this);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EOFException();
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public long readLong() throws EOFException {
		try {
			return VariableByteCoding.decode(this);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new EOFException();
		}
	}

	public String readUTF() throws IOException, EOFException {
		int len = readInt();
		byte data[] = new byte[len];
		read(data);
		String s;
		try {
			s = new String(data, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			s = new String(data);
		}
		return s;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  count  Description of the Parameter
	 */
	public void skip(int count) {
		for (int i = 0; i < count && available() > 0; i++)
			VariableByteCoding.decode(this);
	}

	public void copyTo(VariableByteOutputStream os) {
		VariableByteCoding.copyTo(this, os.buf);
	}
}
