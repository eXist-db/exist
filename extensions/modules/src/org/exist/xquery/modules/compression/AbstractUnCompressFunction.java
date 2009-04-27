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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;

import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.xmldb.XMLDBAbstractCollectionManipulator;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * Compresses a sequence of resources and/or collections into a Tar file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public abstract class AbstractUnCompressFunction extends XMLDBAbstractCollectionManipulator {

	private HashSet<String> list = new HashSet<String>();
	
	protected boolean doUncompress(String name){
		boolean f = list.isEmpty() || list.contains(name);
		if (!f){
			for (String i : list){
				f |= name.startsWith(i + "/");
				if (f) break;
			}
		}
		return  f; 
	}

	private ByteArrayInputStream preEval(Base64Binary data, Sequence list) throws XPathException{
		for (SequenceIterator i = list.iterate(); i.hasNext();){
			String r = i.nextItem().toString();
			if (r.startsWith("/")){
				r = r.substring(1);
			}
			if (r.endsWith("/")){
				r = r.substring(0, r.length() - 1);
			}
			this.list.add(r);
		}
		return new ByteArrayInputStream(data.getBinaryData());
	}

	public AbstractUnCompressFunction(XQueryContext context,FunctionSignature signature) {
		super(context, signature);
		setCollectionParameterNubmer(2);
	}

	protected Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence) throws XPathException {
		return unCompress(preEval((Base64Binary) args[0].itemAt(0), args[1]), collection);
	}
		
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}
		if (args.length > getCollectionParameterNumber()){
			return super.eval(args, contextSequence);
		} else {
			return unCompress(preEval((Base64Binary) args[0].itemAt(0), args[1]));
		}
	}
	
	private Item createEntry(String name, String type, MimeType mime, Item content) throws XPathException{
		
		MemTreeBuilder builder = context.getDocumentBuilder();

		builder.startDocument();
		builder.startElement(new QName("entry", null, null), null);
		builder.addAttribute(new QName("name", null, null), name);
		builder.addAttribute(new QName("type", null, null), type);
		if (mime != null){
			builder.addAttribute(new QName("mime-type", null, null), mime.getName());
		}
		if (content != null){
			if (content instanceof AtomicValue){
				builder.characters(content.getStringValue());
			}
			else {
				try {
					((NodeImpl) content).copyTo(null, new DocumentBuilderReceiver(builder));
				} catch (SAXException e) {
					throw new XPathException(getASTNode(), e.getMessage());
				}
			}
		}
		builder.endElement();
		
		return (Item)builder.getDocument().getDocumentElement();
		
	}
	
	private Item createXMLEntry(ByteArrayOutputStream baos, String name, MimeType mime) throws XPathException, SAXException{
		Item content = ModuleUtils.streamToXML(context, new ByteArrayInputStream(baos.toByteArray()));
		return createEntry(name, "xml", mime, content);
	}
		
	private Item createBinaryEntry(ByteArrayOutputStream baos, String name, MimeType mime) throws XPathException{
		Item content = new Base64Binary(baos.toByteArray());
		return createEntry(name, "binary", mime, content);
	}
	
	protected Item createResourceEntry(InputStream is, String name) throws IOException, XPathException{
		MimeType mime = MimeTable.getInstance().getContentTypeFor(name);
		ByteArrayOutputStream baos = baos(is);
		try{
			return createXMLEntry(baos, name, mime);
		} catch(SAXException e){
			return createBinaryEntry(baos, name, mime);
        }
	}
		
	protected Item createCollectionEntry(String name) throws XPathException{
		return createEntry(name, "collection", null, null);
	}	

	private Resource createXMLResource(ByteArrayOutputStream baos, Collection collection, String name) throws XMLDBException, XPathException, SAXException{
		NodeValue content = ModuleUtils.streamToXML(context, new ByteArrayInputStream(baos.toByteArray()));
		Resource resource = collection.createResource(name, "XMLResource");
		ContentHandler handler = ((XMLResource)resource).setContentAsSAX();
		handler.startDocument();
		content.toSAX(context.getBroker(), handler, null);
		handler.endDocument();
		return resource;
	}
		
	private Resource createBinaryResource(ByteArrayOutputStream baos, Collection collection, String name) throws XMLDBException{
		Resource resource = collection.createResource(name, "BinaryResource");
		resource.setContent(baos.toByteArray());
		return resource;
	}
	
	protected Resource createResource(InputStream is, Collection collection, String path) throws IOException, XMLDBException, XPathException{
		Resource resource = null;
		File file = new File(path);
		String name = file.getName();
		path = file.getParent();
		Collection target = (path==null) ? collection : createCollection(collection, path);
	    MimeType mime = MimeTable.getInstance().getContentTypeFor(name);
		ByteArrayOutputStream baos = baos(is);
		try{
			resource = createXMLResource(baos, target, name);
		} catch(SAXException e){
			resource = createBinaryResource(baos, target, name);
        }
		if (resource != null){
			if (mime != null){
				((EXistResource)resource).setMimeType(mime.getName());
			}
			target.storeResource(resource);
		}
		return resource;
	}
	
	private static ByteArrayOutputStream baos(InputStream is) throws IOException{
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		int size;
		byte[] b = new byte[4096];
		while ((size = is.read(b, 0, 4096)) != -1){
			baos.write(b, 0, size);
		}
		baos.flush();
		baos.close();
		return baos;
	}

	protected abstract Sequence unCompress(ByteArrayInputStream bais, Collection collection) throws XPathException;
	
	protected abstract Sequence unCompress(ByteArrayInputStream bais) throws XPathException;
	
}
