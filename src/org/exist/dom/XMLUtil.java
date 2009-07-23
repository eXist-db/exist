
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
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id:
 */
package org.exist.dom;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.xml.transform.TransformerException;

import org.apache.log4j.Logger;

import org.exist.util.serializer.DOMSerializer;
import org.exist.xquery.Constants;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

import org.w3c.dom.DocumentFragment;
import org.xml.sax.InputSource;

/**
 *  Defines some static utility methods. 
 *
 */
public class XMLUtil {
	
	private static Logger LOG = Logger.getLogger(XMLUtil.class.getName());

	public final static String dump(DocumentFragment fragment) {
        StringWriter writer = new StringWriter();
		DOMSerializer serializer = new DOMSerializer();
        serializer.setWriter(writer);
		try {
			serializer.serialize(fragment);
		} catch (TransformerException e) {
		}
		return writer.toString();
	}

	public final static String encodeAttrMarkup(String str) {
		StringBuilder buf = new StringBuilder();
		char ch;
		for (int i = 0; i < str.length(); i++)
			switch (ch = str.charAt(i)) {
				case '&' :
					boolean isEntity = false;
					for (int j = i + 1; j < str.length(); j++) {
						if (str.charAt(j) == ';') {
							isEntity = true;
							break;
						}
						if (!Character.isLetter(str.charAt(j)))
							break;
					}
					if (isEntity)
						buf.append('&');
					else
						buf.append("&amp;");

					break;
				case '<' :
					buf.append("&lt;");
					break;
				case '>' :
					buf.append("&gt;");
					break;
				case '"' :
					buf.append("&quot;");
					break;
				default :
					buf.append(ch);
			}

		return buf.toString();
	}

	public final static String decodeAttrMarkup(String str) {
		StringBuilder out = new StringBuilder(str.length());
		char ch;
		String ent;
		int p;
		for (int i = 0; i < str.length(); i++) {
			ch = str.charAt(i);
			if (ch == '&') {
				p = str.indexOf(';', i);
				if (p != Constants.STRING_NOT_FOUND) {
					ent = str.substring(i + 1, p);
					if (ent.equals("amp"))
						out.append('&');
					else if (ent.equals("lt"))
						out.append('<');
					else if (ent.equals("gt"))
						out.append('>');
					else if (ent.equals("quot"))
						out.append('"');
					i = p;
					continue;
				}
			}
			out.append(ch);
		}
		return out.toString();
	}

	public final static String getEncoding(String xmlDecl) {
		if (xmlDecl == null)
			return null;
		StringBuilder buf = new StringBuilder();
		int p0 = xmlDecl.indexOf("encoding");
		if (p0 == Constants.STRING_NOT_FOUND)
			return null;
		for (int i = p0 + 8; i < xmlDecl.length(); i++)
			if (Character.isWhitespace(xmlDecl.charAt(i))
				|| xmlDecl.charAt(i) == '=')
				continue;
			else if (xmlDecl.charAt(i) == '"') {
				while (xmlDecl.charAt(++i) != '"' && i < xmlDecl.length())
					buf.append(xmlDecl.charAt(i));
				return buf.toString();
			} else
				return null;
		return null;
	}
	
	public final static String getXMLDecl(byte[] data) {
		boolean foundTag = false;
		for (int i = 0; i < data.length && !foundTag; i++)
			if (data[i] == '<') {
				foundTag = true;
				if (data[i + 1] == '?'
					&& data[i + 2] == 'x'
					&& data[i + 3] == 'm'
					&& data[i + 4] == 'l')
					for (int j = i + 5; j < data.length; j++)
						if (data[j] == '?' && data[j + 1] == '>') {
							String xmlDecl = new String(data, i, j - i + 2);
							return xmlDecl;
						}
			}
		return null;
	}

	public final static String readFile(File file) throws IOException {
		return readFile(file, "ISO-8859-1");
	}

	public static String readFile(File file, String defaultEncoding)
		throws IOException {
		// read the file into a string
		return readFile(new FileInputStream(file), defaultEncoding);
	}

	public static String readFile(InputSource is)
		throws IOException {
		// read the file into a string
		return readFile(is.getByteStream(), is.getEncoding());
	}

	public static String readFile(InputStream in, String defaultEncoding)
		throws IOException {
		byte[] chunk = new byte[512];
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int l;
		do {
			l = in.read(chunk);
			if (l > 0)
				out.write(chunk, 0, l);

		} while (l > -1);
		in.close();
		byte[] data = out.toByteArray();
		String xmlDecl = getXMLDecl(data);
		String enc = getEncoding(xmlDecl);
		if (enc == null)
			enc = defaultEncoding;

		try {
			return new String(out.toByteArray(), enc);
		} catch (UnsupportedEncodingException e) {
			LOG.warn(e);
			return new String(out.toByteArray());
		}
	}

	public static String parseValue(String value, String key) {
		int p = value.indexOf(key);
		if (p == Constants.STRING_NOT_FOUND)
			return null;
		return parseValue(value, p);
	}

	public static String parseValue(String value, int p) {
		while ((p < value.length()) && (value.charAt(++p) != '"'))
        {
            // Do nothing
        }

		if (p == value.length())
			return null;

		int e = ++p;

		while ((e < value.length()) && (value.charAt(++e) != '"')){
            // Do nothing
        }

		if (e == value.length())
			return null;

		return value.substring(p, e);
	}
   
}
