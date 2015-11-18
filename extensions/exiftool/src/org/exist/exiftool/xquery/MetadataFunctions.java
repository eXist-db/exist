package org.exist.exiftool.xquery;

import java.io.ByteArrayInputStream;
import org.apache.commons.io.output.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.NativeBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * @author Dulip Withanage <dulip.withanage@gmail.com>
 * @version 1.0
 */
public class MetadataFunctions extends BasicFunction {

    @SuppressWarnings("unused")
    private final static Logger logger = LogManager.getLogger(MetadataFunctions.class);

    public final static FunctionSignature getMetadata = new FunctionSignature(
            new QName("get-metadata", ExiftoolModule.NAMESPACE_URI, ExiftoolModule.PREFIX),
            "extracts the metadata",
            new SequenceType[]{
                new FunctionParameterSequenceType("binary", Type.ANY_URI, Cardinality.ONE, "The binary file from which to extract from")
            },
            new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted metadata")
    );

    /*
    public final static FunctionSignature writeMetadata = new FunctionSignature(
        new QName("write-metadata", ExiftoolModule.NAMESPACE_URI, ExiftoolModule.PREFIX),
        "write the metadata into a binary document",
        new SequenceType[]{
            new FunctionParameterSequenceType("doc",Type.DOCUMENT, Cardinality.ONE, " XML file containing file"),
            new FunctionParameterSequenceType("binary", Type.BASE64_BINARY, Cardinality.ONE, "The binary data into where metadata is written")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "Extracted metadata")
    );
    */
    
    public MetadataFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        String uri = args[0].itemAt(0).getStringValue();

        try {
            if (uri.toLowerCase().startsWith("http")) {
                //document from the web
                return extractMetadataFromWebResource(uri);


            } else {
                //document from the db
                XmldbURI docUri = XmldbURI.xmldbUriFor(uri);
                return extractMetadataFromLocalResource(docUri);
            }

        } catch (URISyntaxException use) {
            throw new XPathException("Could not parse document URI: " + use.getMessage(), use);
        }

    }

    private Sequence extractMetadataFromLocalResource(XmldbURI docUri) throws XPathException {
        DocumentImpl doc = null;
        try {
            doc = context.getBroker().getXMLResource(docUri, Lock.READ_LOCK);
            if (doc instanceof BinaryDocument) {
                //resolve real filesystem path of binary file
                final Path binaryFile = ((NativeBroker) context.getBroker()).getCollectionBinaryFileFsPath(docUri);
                if (!Files.exists(binaryFile)) {
                    throw new XPathException("Binary Document at " + docUri.toString() + " does not exist.");
                }
                return exifToolExtract(binaryFile);
            } else {
                throw new XPathException("The binay document at " + docUri.toString() + " cannot be found.");
            }
        } catch (PermissionDeniedException pde) {
            throw new XPathException("Could not access binary document: " + pde.getMessage(), pde);
        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
    }

    private Sequence extractMetadataFromWebResource(String uri) throws XPathException {
        //parse the string uri into a URI object to make sure its valid
        URI u;
        try {
            u = new URI(uri);
            return exifToolWebExtract(u);
        } catch (URISyntaxException ex) {
            throw new XPathException("URI syntax error" + ex.getMessage(), ex);
        }
       
    }

    private Sequence exifToolExtract(final Path binaryFile) throws XPathException {
        final ExiftoolModule module = (ExiftoolModule) getParentModule();
        try {
            final Process p = Runtime.getRuntime().exec(module.getPerlPath() + " " + module.getExiftoolPath() + " -X -struct " + binaryFile.toAbsolutePath().toString());
            try(final InputStream stdIn = p.getInputStream();
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                //buffer stdin
                int read = -1;
                byte buf[] = new byte[4096];
                while ((read = stdIn.read(buf)) > -1) {
                    baos.write(buf, 0, read);
                }

                //make sure process is complete
                p.waitFor();

                return ModuleUtils.inputSourceToXML(context, new InputSource(new ByteArrayInputStream(baos.toByteArray())));
            }
        } catch (final IOException ex) {
            throw new XPathException("Could not execute the Exiftool " + ex.getMessage(), ex);
        } catch (final SAXException saxe) {
            throw new XPathException("Could not parse output from the Exiftool " + saxe.getMessage(), saxe);
        } catch (final InterruptedException ie) {
            throw new XPathException("Could not execute the Exiftool " + ie.getMessage(), ie);
        }
    }

    private Sequence exifToolWebExtract(final URI uri) throws XPathException {
        final ExiftoolModule module = (ExiftoolModule) getParentModule();
        try {
            final Process p = Runtime.getRuntime().exec(module.getExiftoolPath()+" -fast -X -");

            try(final InputStream stdIn = p.getInputStream();
                    final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                try(final OutputStream stdOut = p.getOutputStream()) {
                    final Source src = SourceFactory.getSource(context.getBroker(), null, uri.toString(), false);
                    try(final InputStream isSrc = src.getInputStream()) {

                        //write the remote data to stdOut
                        int read = -1;
                        byte buf[] = new byte[4096];
                        while ((read = isSrc.read(buf)) > -1) {
                            stdOut.write(buf, 0, read);
                        }
                    }
                }

                //read stdin to buffer
                int read = -1;
                byte buf[] = new byte[4096];
                while ((read = stdIn.read(buf)) > -1) {
                    baos.write(buf, 0, read);
                }

                //make sure process is complete
                p.waitFor();

                return ModuleUtils.inputSourceToXML(context, new InputSource(new ByteArrayInputStream(baos.toByteArray())));
            }

        } catch (final IOException ex) {
            throw new XPathException("Could not execute the Exiftool " + ex.getMessage(), ex);
        } catch(final PermissionDeniedException pde) {
            throw new XPathException("Could not execute the Exiftool " + pde.getMessage(), pde);
        } catch (final SAXException saxe) {
            throw new XPathException("Could not parse output from the Exiftool " + saxe.getMessage(), saxe);
        } catch (final InterruptedException ie) {
            throw new XPathException("Could not execute the Exiftool " + ie.getMessage(), ie);
        }
    }
}
