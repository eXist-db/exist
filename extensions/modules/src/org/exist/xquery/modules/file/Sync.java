package org.exist.xquery.modules.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.*;

import javax.xml.XMLConstants;
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
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
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
	
	public Sync(final XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
		if (!context.getSubject().hasDbaRole()) {
			throw new XPathException(this, "Function file:sync is only available to the DBA role");
		}
		
		final String collectionPath = args[0].getStringValue();
		Date startDate = null;
		if (args[2].hasOne()) {
			DateTimeValue dtv = (DateTimeValue) args[2].itemAt(0);
			startDate = dtv.getDate();
		}
        
		final String target = args[1].getStringValue();
        Path targetDir = FileModuleHelper.getFile(target);
        
		
		context.pushDocumentContext();
		final MemTreeBuilder output = context.getDocumentBuilder();
		try {
			if (!targetDir.isAbsolute()) {
				final Optional<Path> home = context.getBroker().getConfiguration().getExistHome();
				targetDir = FileUtils.resolve(home, target);
			}
			
			output.startDocument();
			output.startElement(new QName("sync", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("collection", FileModule.NAMESPACE_URI), collectionPath);
			output.addAttribute(new QName("dir", FileModule.NAMESPACE_URI), targetDir.toAbsolutePath().toString());
			
			saveCollection(XmldbURI.create(collectionPath), targetDir, startDate, output);
			
			output.endElement();
			output.endDocument();
        } catch(final PermissionDeniedException | LockException e) {
            throw new XPathException(this, e);
		} finally {
			context.popDocumentContext();
		}
		return output.getDocument();
	}

	private void saveCollection(final XmldbURI collectionPath, Path targetDir, final Date startDate, final MemTreeBuilder output) throws PermissionDeniedException, LockException {
		try {
			targetDir = Files.createDirectories(targetDir);
		} catch(final IOException ioe) {
			reportError(output, "Failed to create output directory: " + targetDir.toAbsolutePath().toString() +
					" for collection " + collectionPath);
			return;
		}

		if (!Files.isWritable(targetDir)) {
			reportError(output, "Failed to write to output directory: " + targetDir.toAbsolutePath().toString());
			return;
		}
		
		List<XmldbURI> subcollections = null;
		try(final Collection collection = context.getBroker().openCollection(collectionPath, LockMode.READ_LOCK)) {
			if (collection == null) {
				reportError(output, "Collection not found: " + collectionPath);
				return;
			}
			for (final Iterator<DocumentImpl> i = collection.iterator(context.getBroker()); i.hasNext(); ) {
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
			for (final Iterator<XmldbURI> i = collection.collectionIterator(context.getBroker()); i.hasNext(); ) {
				subcollections.add(i.next());
			}
		}
		
		for (final XmldbURI childURI : subcollections) {
			final Path childDir = targetDir.resolve(childURI.lastSegment().toString());
			saveCollection(collectionPath.append(childURI), childDir, startDate, output);
		}
	}

	private void reportError(final MemTreeBuilder output,final String msg) {
		output.startElement(new QName("error", FileModule.NAMESPACE_URI), null);
		output.characters(msg);
		output.endElement();
	}

	private void saveXML(final Path targetDir, final DocumentImpl doc, final MemTreeBuilder output) {
		Path targetFile = targetDir.resolve(doc.getFileURI().toASCIIString());
		final SAXSerializer sax = (SAXSerializer)SerializerPool.getInstance().borrowObject( SAXSerializer.class );
		try {
			if (Files.exists(targetFile) && Files.getLastModifiedTime(targetFile).compareTo(FileTime.fromMillis(doc.getMetadata().getLastModified())) >= 0) {
				return;
			}
    	    boolean isRepoXML = Files.exists(targetFile) && FileUtils.fileName(targetFile).equals("repo.xml");

			output.startElement(new QName("update", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("file", XMLConstants.NULL_NS_URI), targetFile.toAbsolutePath().toString());
			output.addAttribute(new QName("name", XMLConstants.NULL_NS_URI), doc.getFileURI().toString());
			output.addAttribute(new QName("collection", XMLConstants.NULL_NS_URI), doc.getCollection().getURI().toString());
			output.addAttribute(new QName("type", XMLConstants.NULL_NS_URI), "xml");
			output.addAttribute(new QName("modified", XMLConstants.NULL_NS_URI), new DateTimeValue(new Date(doc.getMetadata().getLastModified())).getStringValue());
            output.endElement();

            if (isRepoXML) {
                processRepoDesc(targetFile, doc, sax, output);
            } else {
				try(final Writer writer = new OutputStreamWriter(Files.newOutputStream(targetFile), "UTF-8")) {
					sax.setOutput(writer, DEFAULT_PROPERTIES);
					Serializer serializer = context.getBroker().getSerializer();
					serializer.reset();
					serializer.setProperties(DEFAULT_PROPERTIES);

					serializer.setSAXHandlers(sax, sax);
					serializer.toSAX(doc);
				}
            }
		} catch (final IOException e) {
			reportError(output, "IO error while saving file: " + targetFile.toAbsolutePath().toString());
		} catch (final SAXException e) {
			reportError(output, "SAX exception while saving file " + targetFile.toAbsolutePath().toString() + ": " + e.getMessage());
		} catch (final XPathException e) {
			reportError(output, e.getMessage());
		} finally {
			SerializerPool.getInstance().returnObject(sax);
		}		
	}

    /**
     * Merge repo.xml modified by user with original file. This is necessary because we have to
     * remove sensitive information during upload (default password) and need to restore it
     * when the package is synchronized back to disk.
     */
    private void processRepoDesc(final Path targetFile, final DocumentImpl doc, final SAXSerializer sax, final MemTreeBuilder output) {
        try {
            final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final Document original = builder.parse(targetFile.toFile());

            try (final Writer writer = new OutputStreamWriter(Files.newOutputStream(targetFile), "UTF-8")) {
                sax.setOutput(writer, DEFAULT_PROPERTIES);
                
                final StreamSource stylesource = new StreamSource(Sync.class.getResourceAsStream("repo.xsl"));
                
                final SAXTransformerFactory factory = TransformerFactoryAllocator.getTransformerFactory(context.getBroker().getBrokerPool());
                final TransformerHandler handler = factory.newTransformerHandler(stylesource);
                handler.getTransformer().setParameter("original", original.getDocumentElement());
                handler.setResult(new SAXResult(sax));
                
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                serializer.setProperties(DEFAULT_PROPERTIES);
                serializer.setSAXHandlers(handler, handler);
                
                serializer.toSAX(doc);
            }
        } catch (final ParserConfigurationException e) {
            reportError(output, "Parser exception while saving file " + targetFile.toAbsolutePath().toString() + ": " + e.getMessage());
        } catch (final SAXException e) {
            reportError(output, "SAX exception while saving file " + targetFile.toAbsolutePath().toString() + ": " + e.getMessage());
        } catch (final IOException e) {
            reportError(output, "IO exception while saving file " + targetFile.toAbsolutePath().toString() + ": " + e.getMessage());
        } catch (final TransformerException e) {
            reportError(output, "Transformation exception while saving file " + targetFile.toAbsolutePath().toString() + ": " + e.getMessage());
        }
    }

	private void saveBinary(final Path targetDir, final BinaryDocument binary, final MemTreeBuilder output) {
		final Path targetFile = targetDir.resolve(binary.getFileURI().toASCIIString());
		try {
			if (Files.exists(targetFile) && Files.getLastModifiedTime(targetFile).compareTo(FileTime.fromMillis(binary.getMetadata().getLastModified())) >= 0) {
				return;
			}

			output.startElement(new QName("update", FileModule.NAMESPACE_URI), null);
			output.addAttribute(new QName("file"), targetFile.toAbsolutePath().toString());
			output.addAttribute(new QName("name"), binary.getFileURI().toString());
			output.addAttribute(new QName("collection"), binary.getCollection().getURI().toString());
			output.addAttribute(new QName("type"), "binary");
			output.addAttribute(new QName("modified"), new DateTimeValue(new Date(binary.getMetadata().getLastModified())).getStringValue());
            output.endElement();

			try(final InputStream is = context.getBroker().getBinaryResource(binary)) {
				Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (final IOException e) {
			reportError(output, "IO error while saving file: " + targetFile.toAbsolutePath().toString());
		} catch (final Exception e) {
			reportError(output, e.getMessage());
		}
	}
}
