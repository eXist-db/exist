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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.exist.dom.QName;
import org.exist.memtree.InMemoryNodeSet;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * Compresses a sequence of resources and/or collections into a Zip file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class UnZipFunction extends AbstractUnCompressFunction {

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("unzip", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
					"UnZip's resources listed in $b form data provided in $a into collection $c." +
					"if $b is empty unZip's all resources",
					new SequenceType[] {
							new SequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE), 
							new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)},
					new SequenceType(Type.EMPTY, Cardinality.EMPTY)),
					
			new FunctionSignature(
					new QName("unzip", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
					"UnZip's resources listed in $b form data provided in $a into result sequence." +
					"if $b is empty unZip's all resources. " +
					"Every zip's enry is XML fragment kind of: <entry name='entry-name' type='entry-type'>content</entry>. " +
					"Where 'entry-name' is name of resourse or collection, " +
					"'entry-type' is type of entry: 'xml', 'binary' or 'collection', " +
					"'content' is content of resource or empty for collection. " +
					"For binary resources the content is BASE64_BINARY.",
					new SequenceType[] {
							new SequenceType(Type.BASE64_BINARY, Cardinality.EXACTLY_ONE),
							new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)},
					new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE))
			};

	public UnZipFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	protected Sequence unCompress(ByteArrayInputStream bais, Collection collection) throws XPathException {
		ZipInputStream zis = new ZipInputStream(bais);
		try {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null){
				if (doUncompress(entry.getName())){
					if (entry.isDirectory()){
						createCollectionPath(collection, entry.getName());
					} else {
						createResource(zis, collection, entry.getName());
					}
				}
			}
		} catch (IOException e) {
			throw new XPathException(this, e.getMessage());
		} catch (XMLDBException e) {
			throw new XPathException(this, e.getMessage());
		}
		return Sequence.EMPTY_SEQUENCE;
	}

	protected Sequence unCompress(ByteArrayInputStream bais) throws XPathException {
		Sequence result = new InMemoryNodeSet();
		ZipInputStream zis = new ZipInputStream(bais);
		try {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null){
				if (doUncompress(entry.getName())){
					if (entry.isDirectory()){
						result.add(createCollectionEntry(entry.getName()));
					} else {
						result.add(createResourceEntry(zis, entry.getName()));
					}
				}
			}
		} catch (IOException e) {
			throw new XPathException(this, e.getMessage());
		}
		return result;
	}

}
