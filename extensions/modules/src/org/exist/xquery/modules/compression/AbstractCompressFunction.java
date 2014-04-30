/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2007-2013 The eXist-db Project
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
import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Base64Decoder;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URI;
import java.util.Iterator;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Compresses a sequence of resources and/or collections
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @author Leif-Jöran Olsson <ljo@exist-db.org>
 * @version 1.0
 */
public abstract class AbstractCompressFunction extends BasicFunction
{
    private final static Logger logger = Logger.getLogger(AbstractCompressFunction.class);

    protected final static SequenceType SOURCES_PARAM = new FunctionParameterSequenceType("sources", Type.ANY_TYPE, Cardinality.ONE_OR_MORE,
            "The sequence of URI's and/or Entrys. If an URI points to a collection then the collection, its resources and sub-collections are zipped recursively. " +
            "If URI points to file (available only to the DBA role.) then file or directory are zipped. " +
            "An Entry takes the format <entry name=\"filename.ext\" type=\"collection|uri|binary|xml|text\" method=\"deflate|store\">data</entry>. The method attribute is only effective for the compression:zip function.");
    protected final static SequenceType COLLECTION_HIERARCHY_PARAM = new FunctionParameterSequenceType("use-collection-hierarchy", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Indicates whether the Collection hierarchy (if any) should be preserved in the zip file.");
    protected final static SequenceType STRIP_PREFIX_PARAM = new FunctionParameterSequenceType("strip-prefix", Type.STRING, Cardinality.EXACTLY_ONE, "This prefix is stripped from the Entrys name");


    public AbstractCompressFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }
	
	private String removeLeadingOffset(String uri, String stripOffset){
		// remove leading offset
		if (uri.startsWith(stripOffset)) {
			uri = uri.substring(stripOffset.length());
		}
		// remove leading /
		if (uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		return uri;
	}

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// are there some uri's to tar?
		if (args[0].isEmpty()) {
			return Sequence.EMPTY_SEQUENCE;
		}

		// use a hierarchy in the tar file?
		boolean useHierarchy = args[1].effectiveBooleanValue();

		// Get offset
		String stripOffset = "";
		if (args.length == 3) {
			stripOffset = args[2].getStringValue();
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		OutputStream os = stream(baos);

		// iterate through the argument sequence
		for (SequenceIterator i = args[0].iterate(); i.hasNext();) {
			Item item = i.nextItem();
			
                        if(item instanceof Element)
                        {
                            Element element = (Element) item;
                            compressElement(os, element, useHierarchy, stripOffset);
                        }
                        else
                        {
                            compressFromUri(os, ((AnyURIValue)item).toURI(), useHierarchy, stripOffset, "", null);
                        }
		}
		try {
			os.close();
		} catch (IOException ioe) {
			throw new XPathException(this, ioe.getMessage());
		}
		return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(baos.toByteArray()));
	}

    private void compressFromUri(OutputStream os, URI uri, boolean useHierarchy, String stripOffset, String method, String resourceName) throws XPathException
        {
            try {
                if ("file".equals(uri.getScheme())) {

                    if (!context.getSubject().hasDbaRole()) {
                        XPathException xPathException = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
                        logger.error("Invalid user", xPathException);
                        throw xPathException;
                    }

                    // got a file
                    File file = new File(uri.getPath());
                    compressFile(os, file, useHierarchy, stripOffset, method, resourceName);

                } else {

                    // try for a doc
                    DocumentImpl doc = null;
                    try
                    {
                        XmldbURI xmldburi = XmldbURI.create(uri);
                        doc = context.getBroker().getXMLResource(xmldburi, Lock.READ_LOCK);

                        if(doc == null)
                        {
                            // no doc, try for a collection
                            Collection col = context.getBroker().getCollection(xmldburi);

                            if(col != null)
                            {
                                // got a collection
                                compressCollection(os, col, useHierarchy, stripOffset);
                            }
                            else
                            {
                                // no doc or collection
                                throw new XPathException(this, "Invalid URI: " + uri.toString());
                            }
                        }
                        else
                        {
                            // got a doc
                            compressResource(os, doc, useHierarchy, stripOffset, method, resourceName);
                        }
                    }
                    catch(PermissionDeniedException pde)
                    {
                        throw new XPathException(this, pde.getMessage());
                    }
                    catch(IOException ioe)
                    {
                        throw new XPathException(this, ioe.getMessage());
                    }
                    catch(SAXException saxe)
                    {
                        throw new XPathException(this, saxe.getMessage());
                    }
                    catch(LockException le)
                    {
                        throw new XPathException(this, le.getMessage());
                    }
                    finally
                    {
                        if(doc != null)
                            doc.getUpdateLock().release(Lock.READ_LOCK);
                    }
                }

            } catch (IOException e) {
                throw new XPathException(this, e.getMessage());
            }

        }

    /**
     * Adds a element to a archive
     *
     * @param os
     *            The Output Stream to add the element to
     * @param file
     *            The file to add to the archive
     * @param useHierarchy
     *            Whether to use a folder hierarchy in the archive file that
     *            reflects the collection hierarchy
     */
    private void compressFile(OutputStream os, File file, boolean useHierarchy, String stripOffset, String method, String name) throws IOException {

        if (file.isFile()) {

            // create an entry in the Tar for the document
            Object entry = null;
            byte[] value = new byte[0];
            CRC32 chksum = new CRC32();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            if(name != null)
            {
                entry = newEntry(name);
            }
            else if (useHierarchy) {
                entry = newEntry(removeLeadingOffset(file.getPath(), stripOffset));
            } else {
                entry = newEntry(file.getName());
            }

            InputStream is = new FileInputStream(file);
            byte[] data = new byte[16384];
            int len = 0;
            while ((len=is.read(data,0,data.length))>0) {
                baos.write(data,0,len);
            }
            is.close();
            value = baos.toByteArray();
            // close the entry
            if (entry instanceof ZipEntry &&
                    "store".equals(method)) {
                ((ZipEntry) entry).setMethod(ZipOutputStream.STORED);
                chksum.update(value);
                ((ZipEntry) entry).setCrc(chksum.getValue());
                ((ZipEntry) entry).setSize(value.length);
            }

            putEntry(os, entry);
            os.write(value);
            closeEntry(os);

        } else {

            for (String i : file.list()) {
                compressFile(os, new File(file, i), useHierarchy, stripOffset, method, null);
            }

        }

    }

    /**
	 * Adds a element to a archive
	 * 
	 * @param os
	 *            The Output Stream to add the element to
	 * @param element
	 *            The element to add to the archive
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the archive file that
	 *            reflects the collection hierarchy
	 */
	private void compressElement(OutputStream os, Element element, boolean useHierarchy, String stripOffset) throws XPathException
        {

            if(!(element.getNodeName().equals("entry") || element.getNamespaceURI().length() > 0))
                throw new XPathException(this, "Item must be type of xs:anyURI or element entry.");

            if(element.getChildNodes().getLength() > 1)
                throw new XPathException(this, "Entry content is not valid XML fragment.");

            String name = element.getAttribute("name");
//            if(name == null)
//                throw new XPathException(this, "Entry must have name attribute.");

            String type = element.getAttribute("type");

            if("uri".equals(type))
            {
                compressFromUri(os, URI.create(element.getFirstChild().getNodeValue()), useHierarchy, stripOffset, element.getAttribute("method"), name);
                return;
            }

            if(useHierarchy)
            {
                name = removeLeadingOffset(name, stripOffset);
            }
            else
            {
                name = name.substring(name.lastIndexOf("/") + 1);
            }

            if("collection".equals(type))
                name += "/";
            
            Object entry = null;

            try
            {
                
                entry = newEntry(name);

                if(!"collection".equals(type))
                {
                    byte[] value;
                    CRC32 chksum = new CRC32();
                    Node content = element.getFirstChild();


                    if(content == null)
                    {
                        value = new byte[0];
                    }
                    else
                    {
                        if(content.getNodeType() == Node.TEXT_NODE)
                        {
                            String text = content.getNodeValue();
                            Base64Decoder dec = new Base64Decoder();
                            if("binary".equals(type))
                            {
                                //base64 binary
                                dec.translate(text);
                                value = dec.getByteArray();
                            }
                            else
                            {
                                //text
                                value = text.getBytes();
                            }
                        }
                        else
                        {
                            //xml
                            Serializer serializer = context.getBroker().getSerializer();
                            serializer.setUser(context.getUser());
                            serializer.setProperty("omit-xml-declaration", "no");
                            getDynamicSerializerOptions(serializer);
                            value = serializer.serialize((NodeValue) content).getBytes();
                        }
                    }

                    if (entry instanceof ZipEntry &&
                        "store".equals(element.getAttribute("method"))) {
                        ((ZipEntry) entry).setMethod(ZipOutputStream.STORED);
                        chksum.update(value);
                        ((ZipEntry) entry).setCrc(chksum.getValue());
                        ((ZipEntry) entry).setSize(value.length);
                    }
                    putEntry(os, entry);
                    
                    os.write(value);
                }
            }
            catch(IOException ioe)
            {
                throw new XPathException(this, ioe.getMessage(), ioe);
            }
            catch(SAXException saxe)
            {
                throw new XPathException(this, saxe.getMessage(), saxe);
            }
            finally
            {
                if(entry != null)
                    try
                    {
                        closeEntry(os);
                    }
                    catch(IOException ioe)
                    {
                        throw new XPathException(this, ioe.getMessage(), ioe);
                    }
            }
	}

    private void getDynamicSerializerOptions(Serializer serializer) throws SAXException {
        final Option option = context.getOption(Option.SERIALIZE_QNAME);
        if (option != null) {
            final String[] params = option.tokenizeContents();
            for (final String param : params) {
                // OutputKeys.INDENT
                final String[] kvp = Option.parseKeyValuePair(param);
                serializer.setProperty(kvp[0], kvp[1]);
            }
        }
    }

	/**
	 * Adds a document to a archive
	 * 
	 * @param os
	 *            The Output Stream to add the document to
	 * @param doc
	 *            The document to add to the archive
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the archive file that
	 *            reflects the collection hierarchy
	 */
	private void compressResource(OutputStream os, DocumentImpl doc, boolean useHierarchy, String stripOffset, String method, String name) throws IOException, SAXException {
		// create an entry in the Tar for the document
		Object entry = null;
        byte[] value = new byte[0];
        CRC32 chksum = new CRC32();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(); 

                if(name != null)
                {
                    entry = newEntry(name);
                }
                else if (useHierarchy) {
			String docCollection = doc.getCollection().getURI().toString();
			XmldbURI collection = XmldbURI.create(removeLeadingOffset(docCollection, stripOffset));
			entry = newEntry(collection.append(doc.getFileURI()).toString());
		} else {
			entry = newEntry(doc.getFileURI().toString());
		}

		if (doc.getResourceType() == DocumentImpl.XML_FILE) {
			// xml file
			Serializer serializer = context.getBroker().getSerializer();
			serializer.setUser(context.getUser());
			serializer.setProperty("omit-xml-declaration", "no");
            getDynamicSerializerOptions(serializer);
            String strDoc = serializer.serialize(doc);
            value = strDoc.getBytes();            
		} else if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
			// binary file
            InputStream is = context.getBroker().getBinaryResource((BinaryDocument)doc);
			byte[] data = new byte[16384];
            int len = 0;
            while ((len=is.read(data,0,data.length))>0) {
            	baos.write(data,0,len);
            }
            is.close();
            value = baos.toByteArray();
		}
		// close the entry
        if (entry instanceof ZipEntry &&
            "store".equals(method)) {
            ((ZipEntry) entry).setMethod(ZipOutputStream.STORED);
            chksum.update(value);
            ((ZipEntry) entry).setCrc(chksum.getValue());
            ((ZipEntry) entry).setSize(value.length);
        }

		putEntry(os, entry);
        os.write(value);
		closeEntry(os);
	}
	
	/**
	 * Adds a Collection and its child collections and resources recursively to
	 * a archive
	 * 
	 * @param os
	 *            The Output Stream to add the document to
	 * @param col
	 *            The Collection to add to the archive
	 * @param useHierarchy
	 *            Whether to use a folder hierarchy in the archive file that
	 *            reflects the collection hierarchy
	 */
	private void compressCollection(OutputStream os, Collection col, boolean useHierarchy, String stripOffset) throws IOException, SAXException, LockException, PermissionDeniedException {
		// iterate over child documents
		MutableDocumentSet childDocs = new DefaultDocumentSet();
		col.getDocuments(context.getBroker(), childDocs);
		for (Iterator<DocumentImpl> itChildDocs = childDocs.getDocumentIterator(); itChildDocs
				.hasNext();) {
			DocumentImpl childDoc = (DocumentImpl) itChildDocs.next();
			childDoc.getUpdateLock().acquire(Lock.READ_LOCK);
			try {
				compressResource(os, childDoc, useHierarchy, stripOffset, "", null);
			} finally {
				childDoc.getUpdateLock().release(Lock.READ_LOCK);
			}
		}
		// iterate over child collections
		for (Iterator<XmldbURI> itChildCols = col.collectionIterator(context.getBroker()); itChildCols.hasNext();) {
			// get the child collection
			XmldbURI childColURI = (XmldbURI) itChildCols.next();
			Collection childCol = context.getBroker().getCollection(col.getURI().append(childColURI));
			// recurse
			compressCollection(os, childCol, useHierarchy, stripOffset);
		}
	}
	
	protected abstract OutputStream stream(ByteArrayOutputStream baos); 
	
	protected abstract Object newEntry(String name);
	
	protected abstract void putEntry(Object os, Object entry) throws IOException;

	protected abstract void closeEntry(Object os) throws IOException;
}
