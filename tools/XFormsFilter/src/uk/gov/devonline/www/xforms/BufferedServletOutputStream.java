/*
 *  XFormsFilter: BufferedServletOutputStream
 *  Copyright (C) 2006 Matthijs Wensveen <m.wensveen@func.nl>
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
 *  $Id: XFormsFilter.java 4565 2006-10-12 12:42:18 +0000 (Thu, 12 Oct 2006) deliriumsky $
 */
package uk.gov.devonline.www.xforms;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import javax.servlet.ServletOutputStream;

/**
 * @author Matthijs Wensveen <m.wensveen@func.nl>
 */
public class BufferedServletOutputStream extends ServletOutputStream
{
    private PrintStream stream;
    
	/** FilterServletOutputStream
     * 
     * @param output
     */
    public BufferedServletOutputStream(OutputStream output)
    {
        stream = new PrintStream(output);
    }
    
    /**
	 * @param b
	 * @see java.io.PrintStream#print(boolean)
	 */
	public void print(boolean b) {
		stream.print(b);
	}

	/**
	 * @param c
	 * @see java.io.PrintStream#print(char)
	 */
	public void print(char c) {
		stream.print(c);
	}

	/**
	 * @param d
	 * @see java.io.PrintStream#print(double)
	 */
	public void print(double d) {
		stream.print(d);
	}

	/**
	 * @param f
	 * @see java.io.PrintStream#print(float)
	 */
	public void print(float f) {
		stream.print(f);
	}

	/**
	 * @param i
	 * @see java.io.PrintStream#print(int)
	 */
	public void print(int i) {
		stream.print(i);
	}

	/**
	 * @param l
	 * @see java.io.PrintStream#print(long)
	 */
	public void print(long l) {
		stream.print(l);
	}

	/**
	 * @param s
	 * @see java.io.PrintStream#print(java.lang.String)
	 */
	public void print(String s) {
		stream.print(s);
	}

	/**
	 * 
	 * @see java.io.PrintStream#println()
	 */
	public void println() {
		stream.println();
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(boolean)
	 */
	public void println(boolean x) {
		stream.println(x);
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(char)
	 */
	public void println(char x) {
		stream.println(x);
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(double)
	 */
	public void println(double x) {
		stream.println(x);
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(float)
	 */
	public void println(float x) {
		stream.println(x);
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(int)
	 */
	public void println(int x) {
		stream.println(x);
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(long)
	 */
	public void println(long x) {
		stream.println(x);
	}

	/**
	 * @param x
	 * @see java.io.PrintStream#println(java.lang.String)
	 */
	public void println(String x) {
		stream.println(x);
	}

 	/**
	 * @param buf
	 * @param off
	 * @param len
	 * @see java.io.PrintStream#write(byte[], int, int)
	 */
	public void write(byte[] buf, int off, int len) {
		stream.write(buf, off, len);
	}

	/**
	 * @param b
	 * @throws IOException
	 * @see java.io.FilterOutputStream#write(byte[])
	 */
	public void write(byte[] b) throws IOException {
		stream.write(b);
	}

	/**
	 * @param b
	 * @see java.io.PrintStream#write(int)
	 */
	public void write(int b) {
		stream.write(b);
	}

	@Override
	public void close() throws IOException {
		stream.close();
		super.close();
	}

	@Override
	public void flush() throws IOException {
		stream.flush();
		super.flush();
	} 
}
