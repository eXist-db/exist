/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 * $Id$
 */
package org.exist.xquery.modules.compression;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Compresses a sequence of resources and/or collections into a Zip file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class ZipFunction extends AbstractCompressFunction {

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("zip", CompressionModule.NAMESPACE_URI,
							CompressionModule.PREFIX),
							"Zip's resources and/or collections. $a is a sequence of URI's and/or entries, if a URI points to a collection"
							+ "then the collection, its resources and sub-collections are zipped recursively. "
							+ "Entry is a XML fragment that can contain xml or binary content. "
							+ "More detailed for entry look compression:unzip($a, $b). "
							+ "$b indicates whether to use the collection hierarchy in the zip file.",
					new SequenceType[] {
							new SequenceType(Type.ANY_TYPE,
									Cardinality.ONE_OR_MORE),
							new SequenceType(Type.BOOLEAN,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.BASE64_BINARY,
							Cardinality.ZERO_OR_MORE)),
			new FunctionSignature(
					new QName("zip", CompressionModule.NAMESPACE_URI,
							CompressionModule.PREFIX),
							"Zip's resources and/or collections. $a is a sequence of URI's and/or entries, if a URI points to a collection"
							+ "then the collection, its resources and sub-collections are zipped recursively. "
							+ "Entry is a XML fragment that can contain xml or binary content. "
							+ "More detailed for entry look compression:unzip($a, $b). "
							+ "$b indicates whether to use the collection hierarchy in the zip file."
							+ "$c is removed from the beginning of each file path.",
					new SequenceType[] {
							new SequenceType(Type.ANY_TYPE,
									Cardinality.ONE_OR_MORE),
							new SequenceType(Type.BOOLEAN,
									Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING,
									Cardinality.EXACTLY_ONE) },
					new SequenceType(Type.BASE64_BINARY,
							Cardinality.ZERO_OR_MORE)) };

	public ZipFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	protected void closeEntry(Object os) throws IOException {
		((ZipOutputStream) os).closeEntry();
	}

	protected Object newEntry(String name) {
		return new ZipEntry(name);
	}

	protected void putEntry(Object os, Object entry) throws IOException {
		((ZipOutputStream) os).putNextEntry((ZipEntry) entry);
	}

	protected OutputStream stream(ByteArrayOutputStream baos) {
		return new ZipOutputStream(baos);
	}

}
