/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *
 *  $Id$
 */

package org.exist.xquery.modules.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;



/**
 * Generic unicode textreader, which will use BOM mark
 * to identify the encoding to be used. If BOM is not found
 * then use a given default or system encoding.  This is a bug fix 
 * workaround for a known issue with InputStreamReader not detecting and
 * ignoring the UTF-* BOM (EF BB BF).
 *
 * http://www.unicode.org/unicode/faq/utf_bom.html
 * BOMs:
 *   00 00 FE FF    = UTF-32, big-endian
 *   FF FE 00 00    = UTF-32, little-endian
 *   EF BB BF       = UTF-8,
 *   FE FF          = UTF-16, big-endian
 *   FF FE          = UTF-16, little-endian
 * 
 * Win2k Notepad:
 *   Unicode format = UTF-16LE
 *
 * Based on code by Thomas Weidenfeller and  Aki Nieminen
 *
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @serial 2008-03-06
 * @version 1.1
 */

public class UnicodeReader extends Reader 
{
	PushbackInputStream internalIn;
	InputStreamReader   internalIn2 = null;
	String              defaultEnc;
	
	private static final int BOM_SIZE = 4;
	
	
	/**
    *
    * @param in  inputstream to be read
    */
	
	public UnicodeReader( InputStream in ) 
	{
		internalIn = new PushbackInputStream( in, BOM_SIZE );
		this.defaultEnc = null;
	}

	
	/**
    *
    * @param in  inputstream to be read
    * @param defaultEnc default encoding if stream does not have 
    *                   BOM marker. Give NULL to use system-level default.
    */
	
	public UnicodeReader( InputStream in, String defaultEnc ) 
	{
		internalIn = new PushbackInputStream( in, BOM_SIZE );
		this.defaultEnc = defaultEnc;
	}
	
	
	public String getDefaultEncoding() 
	{
		return( defaultEnc );
	}
	
	
	/**
     * Get stream encoding or NULL if stream is uninitialized.
     * Call init() or read() method to initialize it.
	 *
	 * @return the encoding
     */
	public String getEncoding() 
	{
		String ret = null;
		
		if( internalIn2 != null ) {
			ret = internalIn2.getEncoding();
		}
		
		return( ret );
	}
	
	
	/**
     * Read-ahead four bytes and check for BOM marks. Extra bytes are
     * unread back to the stream, only BOM bytes are skipped.
	 *
	 * @throws IOException if an I/O error occurs
     */
	protected void init() throws IOException 
	{
		if( internalIn2 == null ) {
		
			String encoding;
			byte bom[] = new byte[BOM_SIZE];
			int n;
			int unread;
			n = internalIn.read( bom, 0, bom.length );
			
			if( (bom[0] == (byte)0x00) && (bom[1] == (byte)0x00) && (bom[2] == (byte)0xFE) && (bom[3] == (byte)0xFF) ) {
				encoding = "UTF-32BE";
				unread = n - 4;
			} else if( (bom[0] == (byte)0xFF) && (bom[1] == (byte)0xFE) && (bom[2] == (byte)0x00) && (bom[3] == (byte)0x00) ) {
				encoding = "UTF-32LE";
				unread = n - 4;
			} else if(  (bom[0] == (byte)0xEF) && (bom[1] == (byte)0xBB) && (bom[2] == (byte)0xBF) ) {
				encoding = "UTF-8";
				unread = n - 3;
			} else if( (bom[0] == (byte)0xFE) && (bom[1] == (byte)0xFF) ) {
				encoding = "UTF-16BE";
				unread = n - 2;
			} else if( (bom[0] == (byte)0xFF) && (bom[1] == (byte)0xFE) ) {
				encoding = "UTF-16LE";
				unread = n - 2;
			} else {
				// Unicode BOM mark not found, unread all bytes
				encoding = defaultEnc;
				unread = n;
			}  

			//System.out.println("read=" + n + ", unread=" + unread);
			
			if( unread > 0 ) {
				internalIn.unread( bom, (n - unread), unread );
			}
			
			// Use given encoding
			if( encoding == null ) {
				internalIn2 = new InputStreamReader( internalIn );
			} else {
				internalIn2 = new InputStreamReader( internalIn, encoding );
			}
		}
	}
	
	
	public void close() throws IOException 
	{
		init();
		internalIn2.close();
	}
	
	public int read( char[] cbuf, int off, int len ) throws IOException 
	{
		init();
		return( internalIn2.read( cbuf, off, len ) );
	}
	
}