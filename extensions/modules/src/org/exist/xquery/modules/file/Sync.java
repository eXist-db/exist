package org.exist.xquery.modules.file;

import java.io.File;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xslt.TransformerFactoryAllocator;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class Sync extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
            new QName("sync", FileModule.NAMESPACE_URI, FileModule.PREFIX),
            "Synchronize a collection with a directory hierarchy. Compares last modified time stamps. " +
            "If $dateTime is given, only resources modified after this time stamp are taken into account. " +
    		"This method is only available to the DBA role.",
            new SequenceType[]{
            	new FunctionParameterSequenceType("collection", Type.STRING, 
                        Cardinality.EXACTLY_ONE, "The collection to sync."),
                new FunctionParameterSequenceType("targetPath", Type.ITEM, 
                        Cardinality.EXACTLY_ONE, "The full path or URI to the directory"),
                new FunctionParameterSequenceType("dateTime", Type.DATE_TIME, 
                        Cardinality.ZERO_OR_ONE, 
                		"Optional: only resources modified after the given date/time will be synchronized.")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if successful, false otherwise")
        );
	
	private final static Properties DEFAULT_PROPERTIES = new Properties();
	static {
		DEFAULT_PROPERTIES.put(OutputKeys.INDENT, "yes");
		DEFAULT_PROPERTIES.put(OutputKeys.OMIT_XML_DECLARATION, "no");
        DEFAULT_PROPERTIES.put(EXistOutputKeys.EXPAND_XINCLUDES, "no");
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
        File targetDir = FileModuleHelper.getFile(target);
        
		
		context.pushDocumentContext();
		MemTreeBuilder output = context.getDocumentBuilder();
		try {
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
        } catch(PermissionDeniedException pde) {
            throw new XPathException(this, pde);
		} finally {
			context.popDocumentContext();
		}
		return output.getDocument();
	}

	private void saveCollection(XmldbURI collectionPath, File targetDir, Date startDate, MemTreeBuilder output) throws PermissionDeniedException {
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
			
			subcollections = new ArrayList<>(collection.getChildCollectionCount(context.getBroker()));
			for (Iterator<XmldbURI> i = collection.collectionIterator(context.getBroker()); i.hasNext(); ) {
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
        boolean isRepoXML = targetFile.exists() && targetFile.getName().equals("repo.xml");
		SAXSerializer sax = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
		try {
			output.startElement(new QName("update", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("file"), targetFile.getAbsolutePath());
			output.addAttribute(new QName("name"), doc.getFileURI().toString());
			output.addAttribute(new QName("collection"), doc.getCollection().getURI().toString());
			output.addAttribute(new QName("type"), "xml");
			output.addAttribute(new QName("modified"), new DateTimeValue(new Date(doc.getMetadata().getLastModified())).getStringValue());
            if (isRepoXML) {
                processRepoDesc(targetFile, doc, sax, output);
            } else {
                OutputStream os = new FileOutputStream(targetFile);
                            try (Writer writer = new OutputStreamWriter(os, "UTF-8")) {
                                sax.setOutput(writer, DEFAULT_PROPERTIES);
                                Serializer serializer = context.getBroker().getSerializer();
                                serializer.reset();
                                serializer.setProperties(DEFAULT_PROPERTIES);
                                
                                serializer.setSAXHandlers(sax, sax);
                                serializer.toSAX(doc);
                            }
            }
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

    /**
     * Merge repo.xml modified by user with original file. This is necessary because we have to
     * remove sensitive information during upload (default password) and need to restore it
     * when the package is synchronized back to disk.
     */
    private void processRepoDesc(File targetFile, DocumentImpl doc, SAXSerializer sax, MemTreeBuilder output) {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document original = builder.parse(targetFile);

            OutputStream os = new FileOutputStream(targetFile);
            try (Writer writer = new OutputStreamWriter(os, "UTF-8")) {
                sax.setOutput(writer, DEFAULT_PROPERTIES);
                
                StreamSource stylesource = new StreamSource(Sync.class.getResourceAsStream("repo.xsl"));
                
                final SAXTransformerFactory factory = TransformerFactoryAllocator.getTransformerFactory(context.getBroker().getBrokerPool());
                TransformerHandler handler = factory.newTransformerHandler(stylesource);
                handler.getTransformer().setParameter("original", original.getDocumentElement());
                handler.setResult(new SAXResult(sax));
                
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                serializer.setProperties(DEFAULT_PROPERTIES);
                serializer.setSAXHandlers(handler, handler);
                
                serializer.toSAX(doc);
            }
        } catch (ParserConfigurationException e) {
            reportError(output, "Parser exception while saving file " + targetFile.getAbsolutePath() + ": " + e.getMessage());
        } catch (SAXException e) {
            reportError(output, "SAX exception while saving file " + targetFile.getAbsolutePath() + ": " + e.getMessage());
        } catch (IOException e) {
            reportError(output, "IO exception while saving file " + targetFile.getAbsolutePath() + ": " + e.getMessage());
        } catch (TransformerException e) {
            reportError(output, "Transformation exception while saving file " + targetFile.getAbsolutePath() + ": " + e.getMessage());
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
			
                        InputStream is;
                    try (OutputStream os = new FileOutputStream(targetFile)) {
                        is = context.getBroker().getBinaryResource(binary);
                        int c;
                        byte buf[] = new byte[4096];
                        while ((c = is.read(buf)) > -1) {
                            os.write(buf, 0, c);
                        }
                    }
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
