
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
package org.exist.util;

import org.w3c.dom.*;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.dom.DocumentImpl;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    18. Juli 2002
 */
public class XMLUtil {

	public final static String dump(DocumentFragment fragment) {
		OutputFormat format = new OutputFormat("xml", "UTF-8", true);
		format.setLineWidth(60);
		format.setOmitXMLDeclaration(true);
		StringWriter writer = new StringWriter();
		XMLSerializer serializer = new XMLSerializer(writer, format);
        try {
		  serializer.serialize(fragment);
        } catch(IOException ioe) {
        }
        return writer.toString();
	}
    
	/**
	 *  Description of the Method
	 *
	 *@param  new_doc   Description of the Parameter
	 *@param  node      Description of the Parameter
	 *@param  new_node  Description of the Parameter
	 */
	protected final static void copyChildren(
		Document new_doc,
		Node node,
		Node new_node) {
		NodeList children = node.getChildNodes();
		Node child;
		Node new_child;
		for (int i = 0; i < children.getLength(); i++) {
			child = children.item(i);
			if (child == null)
				continue;
			switch (child.getNodeType()) {
				case Node.ELEMENT_NODE :
					{
						new_child = copyNode(new_doc, child);
						new_node.appendChild(new_child);
						break;
					}
				case Node.ATTRIBUTE_NODE :
					{
						new_child = copyNode(new_doc, child);
						((Element) new_node).setAttributeNode((Attr) new_child);
						break;
					}
				case Node.TEXT_NODE :
					{
						new_child = copyNode(new_doc, child);
						new_node.appendChild(new_child);
						break;
					}
			}
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  new_doc  Description of the Parameter
	 *@param  node     Description of the Parameter
	 *@return          Description of the Return Value
	 */
	protected final static Node copyNode(Document new_doc, Node node) {
		Node new_node;
		switch (node.getNodeType()) {
			case Node.ELEMENT_NODE :
				new_node = new_doc.createElement(node.getNodeName());
				copyChildren(new_doc, node, new_node);
				return new_node;
			case Node.TEXT_NODE :
				new_node = new_doc.createTextNode(((Text) node).getData());
				return new_node;
			case Node.ATTRIBUTE_NODE :
				new_node = new_doc.createAttribute(node.getNodeName());
				((Attr) new_node).setValue(((Attr) node).getValue());
				return new_node;
			default :
				return null;
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@param  str  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public final static String encodeAttrMarkup(String str) {
		StringBuffer buf = new StringBuffer();
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
		StringBuffer out = new StringBuffer(str.length());
		char ch;
		String ent;
		int p;
		for (int i = 0; i < str.length(); i++) {
			ch = str.charAt(i);
			if (ch == '&') {
				p = str.indexOf(';', i);
				if (-1 < p) {
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

	/**
	 *  Description of the Method
	 *
	 *@param  e  Description of the Parameter
	 *@return    Description of the Return Value
	 */
	public final static String exceptionToString(Exception e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		return sw.toString();
	}

	/**
	 *  Gets the encoding attribute of the XMLUtil class
	 *
	 *@param  xmlDecl  Description of the Parameter
	 *@return          The encoding value
	 */
	public final static String getEncoding(String xmlDecl) {
		if (xmlDecl == null)
			return null;
		StringBuffer buf = new StringBuffer();
		int p0 = xmlDecl.indexOf("encoding");
		if (p0 < 0)
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

	/**
	 *  Gets the firstChildId attribute of the XMLUtil class
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      The firstChildId value
	 */
	public final static long getFirstChildId(DocumentImpl doc, long gid) {
		final int level = doc.getTreeLevel(gid);
		if (level < 0)
			throw new RuntimeException("child index out of bounds");
		final int order = doc.getTreeLevelOrder(level + 1);
		if(order < 0) {
			System.err.println("level " + (level + 1) + " out of bounds: " +
				gid + "; start = " + doc.getLevelStartPoint(level));
			Thread.dumpStack();
		}
			
		return (gid - doc.getLevelStartPoint(level))
			* order
			+ doc.getLevelStartPoint(level + 1);
	}

	/**
	 *  Gets the parentId attribute of the XMLUtil class
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      The parentId value
	 */
	public final static long getParentId(DocumentImpl doc, long gid) {
		int level = doc.getTreeLevel(gid);
		return (gid - doc.getLevelStartPoint(level))
			/ doc.getTreeLevelOrder(level)
			+ doc.getLevelStartPoint(level - 1);
	}

	/**
	 *  Gets the encoding attribute of the XMLUtil class
	 *
	 *@param  data  Description of the Parameter
	 *@return       The encoding value
	 */
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

	/**
	 *  The main program for the XMLUtil class
	 *
	 *@param  args  The command line arguments
	 */
	public static void main(String[] args) {
		String q = "//SPEECH[LINE &amp;= 'fenny snake']";
		System.out.println(XMLUtil.decodeAttrMarkup(q));
	}

	/**
	 *  Description of the Method
	 *
	 *@param  file             Description of the Parameter
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public final static String readFile(File file) throws IOException {
		return readFile(file, "ISO-8859-1");
	}

	/**
	 *  Description of the Method
	 *
	 *@param  file             Description of the Parameter
	 *@param  defaultEncoding  Description of the Parameter
	 *@return                  Description of the Return Value
	 *@exception  IOException  Description of the Exception
	 */
	public static String readFile(File file, String defaultEncoding)
		throws IOException {
		// read the file into a string
		FileInputStream in = new FileInputStream(file);
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
			return new String(out.toByteArray());
		}
	}

	public static String parseValue(String value, String key) {
		int p = value.indexOf(key);
		if (p < 0)
			return null;
		return parseValue(value, p);
	}

	public static String parseValue(String value, int p) {
		while ((p < value.length()) && (value.charAt(++p) != '"'));

		if (p == value.length())
			return null;

		int e = ++p;

		while ((e < value.length()) && (value.charAt(++e) != '"'));

		if (e == value.length())
			return null;

		return value.substring(p, e);
	}
}
