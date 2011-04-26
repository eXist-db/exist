package org.exist.xquery.modules.file;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

public class Sync extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
            new QName("sync", FileModule.NAMESPACE_URI, FileModule.PREFIX),
            "Synchronize a collection with a directory hierarchy. Compares last modified time stamps. " +
            "If $dateTime is given, only resources modified after this time stamp are taken into account. " +
    		"This method is only available to the DBA role.",
            new SequenceType[]{
            	new FunctionParameterSequenceType("collection", Type.STRING, Cardinality.EXACTLY_ONE, "The collection to sync."),
                new FunctionParameterSequenceType("targetPath", Type.STRING, Cardinality.EXACTLY_ONE, "The full path to the directory"),
                new FunctionParameterSequenceType("dateTime", Type.DATE_TIME, Cardinality.ZERO_OR_ONE, 
                		"Optional: only resources modified after the given date/time will be synchronized.")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise")
        );
	
	private final static Properties DEFAULT_PROPERTIES = new Properties();
	static {
		DEFAULT_PROPERTIES.put(OutputKeys.INDENT, "yes");
		DEFAULT_PROPERTIES.put(OutputKeys.OMIT_XML_DECLARATION, "no");
	}
	
	public Sync(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if (!context.getSubject().hasDbaRole())
			throw new XPathException(this, "Function file:sync is only available to the DBA role");
		
		String collectionPath = args[0].getStringValue();
		Date startDate = null;
		if (args[2].hasOne()) {
			DateTimeValue dtv = (DateTimeValue) args[2].itemAt(0);
			startDate = dtv.getDate();
		}
		String target = args[1].getStringValue();
		
		context.pushDocumentContext();
		MemTreeBuilder output = context.getDocumentBuilder();
		try {
			File targetDir = new File(target);
			if (!targetDir.isAbsolute()) {
				File home = context.getBroker().getConfiguration().getExistHome();
				targetDir = new File(home, target);
			}
			
			output.startDocument();
			output.startElement(new QName("sync", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("collection", FileModule.NAMESPACE_URI), collectionPath);
			output.addAttribute(new QName("dir", FileModule.NAMESPACE_URI), targetDir.getAbsolutePath());
			
			saveCollection(XmldbURI.create(collectionPath), targetDir, startDate, output);
			
			output.endElement();
			output.endDocument();
		} finally {
			context.popDocumentContext();
		}
		return output.getDocument();
	}

	private void saveCollection(XmldbURI collectionPath, File targetDir, Date startDate, MemTreeBuilder output) {
		if (!targetDir.exists() && !targetDir.mkdirs()) {
			reportError(output, "Failed to create output directory: " + targetDir.getAbsolutePath() + 
					" for collection " + collectionPath);
			return;
		}
		if (!targetDir.canWrite()) {
			reportError(output, "Failed to write to output directory: " + targetDir.getAbsolutePath());
			return;
		}
		
		List<XmldbURI> subcollections = null;
		Collection collection = null;
		try {
			collection = context.getBroker().openCollection(collectionPath, Lock.READ_LOCK);
			if (collection == null) {
				reportError(output, "Collection not found: " + collectionPath);
				return;
			}
			for (Iterator<DocumentImpl> i = collection.iterator(context.getBroker()); i.hasNext(); ) {
				DocumentImpl doc = i.next();
				if (startDate == null || doc.getMetadata().getLastModified() > startDate.getTime()) {
					if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
						saveBinary(targetDir, (BinaryDocument) doc, output);
					} else {
						saveXML(targetDir, doc, output);
					}
				}
			}
			
			subcollections = new ArrayList<XmldbURI>(collection.getChildCollectionCount());
			for (Iterator<XmldbURI> i = collection.collectionIterator(); i.hasNext(); ) {
				subcollections.add(i.next());
			}
		} finally {
			if (collection != null)
				collection.getLock().release(Lock.READ_LOCK);
		}
		
		for (XmldbURI childURI : subcollections) {
			File childDir = new File(targetDir, childURI.lastSegment().toString());
			saveCollection(collectionPath.append(childURI), childDir, startDate, output);
		}
	}

	private void reportError(MemTreeBuilder output, String msg) {
		output.startElement(new QName("error", FileModule.NAMESPACE_URI), null);
		output.characters(msg);
		output.endElement();
	}

	private void saveXML(File targetDir, DocumentImpl doc, MemTreeBuilder output) {
		File targetFile = new File(targetDir, doc.getFileURI().toASCIIString());
		if (targetFile.exists() && targetFile.lastModified() >= doc.getMetadata().getLastModified()) {
			return;
		}
		SAXSerializer sax = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
		try {
			output.startElement(new QName("update", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("file"), targetFile.getAbsolutePath());
			output.addAttribute(new QName("name"), doc.getFileURI().toString());
			output.addAttribute(new QName("collection"), doc.getCollection().getURI().toString());
			output.addAttribute(new QName("type"), "xml");
			output.addAttribute(new QName("modified"), new DateTimeValue(new Date(doc.getMetadata().getLastModified())).getStringValue());
			
            OutputStream os = new FileOutputStream(targetFile);
			Writer writer = new OutputStreamWriter( os, "UTF-8" );
			
			sax.setOutput(writer, DEFAULT_PROPERTIES);
			Serializer serializer = context.getBroker().getSerializer();
			serializer.reset();
			serializer.setProperties(DEFAULT_PROPERTIES);
			serializer.setReceiver( sax );
			
			serializer.toSAX( doc );	
			
			writer.close();
		} catch (IOException e) {
			reportError(output, "IO error while saving file: " + targetFile.getAbsolutePath());
		} catch (SAXException e) {
			reportError(output, "SAX exception while saving file " + targetFile.getAbsolutePath() + ": " + e.getMessage());
		} catch (XPathException e) {
			reportError(output, e.getMessage());
		} finally {
			output.endElement();
			SerializerPool.getInstance().returnObject( sax );
		}		
	}

	private void saveBinary(File targetDir, BinaryDocument binary, MemTreeBuilder output) {
		File targetFile = new File(targetDir, binary.getFileURI().toASCIIString());
		if (targetFile.exists() && targetFile.lastModified() >= binary.getMetadata().getLastModified()) {
			return;
		}
		try {
			output.startElement(new QName("update", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("file"), targetFile.getAbsolutePath());
			output.addAttribute(new QName("name"), binary.getFileURI().toString());
			output.addAttribute(new QName("collection"), binary.getCollection().getURI().toString());
			output.addAttribute(new QName("type"), "binary");
			output.addAttribute(new QName("modified"), 
					new DateTimeValue(new Date(binary.getMetadata().getLastModified())).getStringValue());
			
			OutputStream os = new FileOutputStream(targetFile);
			InputStream is = context.getBroker().getBinaryResource(binary);
			int c;
			byte buf[] = new byte[4096];
			while ((c = is.read(buf)) > -1) {
				os.write(buf, 0, c);
			}
			os.close();
			is.close();
		} catch (IOException e) {
			reportError(output, "IO error while saving file: " + targetFile.getAbsolutePath());
		} catch (XPathException e) {
			reportError(output, e.getMessage());
		} finally {
			output.endElement();
		}
	}
}
