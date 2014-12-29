package org.expath.exist;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.log4j.Logger;
import org.exist.dom.BinaryDocument;
import org.exist.dom.QName;
import org.exist.dom.DocumentImpl;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.storage.lock.Lock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Created by Alister Pillow on 10/07/2014.
 */
public class ZipFileFunctions extends BasicFunction {

    private static final Logger logger = Logger.getLogger(ZipFileFunctions.class);

    private final static FunctionParameterSequenceType HREF_PARAM =  new FunctionParameterSequenceType("href", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI for locating the Zip file");
    private final static FunctionParameterSequenceType ENTRY_PARAM = new FunctionParameterSequenceType("entry", Type.ELEMENT, Cardinality.EXACTLY_ONE, "A zip:entry element describing the contents of the file");

    private final static String FILE_ENTRIES = "entries";
    private final static String ZIP_FILE = "zip-file";
    private final static String UPDATE_ENTRIES = "update";

    public final static FunctionSignature signatures[] = {
            //zip:entries($href as xs:anyURI) as as element(zip:file)
            new FunctionSignature(
                    new QName(FILE_ENTRIES, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Returns a zip:file element that describes the hierarchical structure of the ZIP file identified by $href in terms of ZIP entries",
                    new SequenceType[]{
                            HREF_PARAM
                    },
                    new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "The document node containing a zip:entry")
            ),
            //zip:zip-file($zip as element(zip:file)) as empty-sequence()
/*            new FunctionSignature(
                    new QName(ZIP_FILE, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Creates a new zip file at zip:file/@href using the children specified within the element",
                    new SequenceType[]{
                            ENTRY_PARAM
                    },
                    new FunctionReturnSequenceType(Type.EMPTY, Cardinality.EMPTY, "The empty sequence.")
            ),*/
            //zip:update-entries($zip as element(zip:file), $output as xs:anyURI) as empty-sequence()
            new FunctionSignature(
                    new QName(UPDATE_ENTRIES, ZipModule.NAMESPACE_URI, ZipModule.PREFIX),
                    "Returns a copy of the zip file at $href, after replacing or adding each binary using the matching path/filename in $paths.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("href", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI for locating the Zip file"),
                            new FunctionParameterSequenceType("paths", Type.STRING, Cardinality.ONE_OR_MORE, "a sequence of file paths"),
                            new FunctionParameterSequenceType("binaries", Type.BASE64_BINARY, Cardinality.ONE_OR_MORE, "a sequence of binaries matching the paths")
                    },
                    new FunctionReturnSequenceType(
                            Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "The new zipped data or the empty sequence if the numbers of $paths and $binaries are different")
            )
    };

    /**
     * SendRequestFunction Constructor
     *
     * @param context   The Context of the calling XQuery
     * @param signature The actual signature of the function
     */
    public ZipFileFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        Sequence result = Sequence.EMPTY_SEQUENCE;
        if (isCalledAs(FILE_ENTRIES)) {
            XmldbURI uri = ((AnyURIValue) args[0].itemAt(0)).toXmldbURI();
            result = extractEntries(uri);
        }
        else if (isCalledAs(ZIP_FILE)) {
            Element zipEntry = (Element)args[0].itemAt(0);
            result = createZip(zipEntry);
        } else if (isCalledAs(UPDATE_ENTRIES)) {
            XmldbURI uri = ((AnyURIValue)args[0].itemAt(0)).toXmldbURI();
            String[] paths = getPaths(args[1]);
            BinaryValue[] newData = getBinaryData(args[2]);
            result = updateZip(uri, paths, newData);
        }

        return result;
    }


    private Sequence updateZip(XmldbURI uri, String[] paths, BinaryValue[] binaries) throws XPathException {
        if (paths.length != binaries.length) {
            throw new XPathException("Different number of paths (" + paths.length + ") and binaries (" + binaries.length + ")");
        }

        ZipFileSource zipFileSource =  new ZipFileFromDb(uri);
        ZipInputStream zis = null;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        HashMap<String, BinaryValue> binariesTable = new HashMap<String, BinaryValue>(paths.length);
        for (int i = 0; i < paths.length; i++) {
            binariesTable.put(paths[i], binaries[i]);
        }

        try
        {
            zis = zipFileSource.getStream();
            ZipOutputStream zos = new ZipOutputStream(baos); // zos is the output - the result
            ZipEntry ze;
            byte[] buffer = new byte[16384];
            int bytes_read;

            while ((ze = zis.getNextEntry())!= null){
                String zen = ze.getName();

                if (binariesTable.containsKey(zen)) { // Replace this entry
                    ZipEntry nze = new ZipEntry(zen);
                    zos.putNextEntry(nze);
                    binariesTable.get(zen).streamBinaryTo(zos);
                    binariesTable.remove(zen);

                } else { // copy this entry to output
                    if (ze.isDirectory()) { // can't add empty directory to Zip
                        ZipEntry dirEntry = new ZipEntry(ze.getName() + System.getProperty("file.separator") + ".");
                        zos.putNextEntry(dirEntry);
                    } else {               // copy file across
                        ZipEntry nze = new ZipEntry(zen);
                        zos.putNextEntry(nze);
                        while((bytes_read = zis.read(buffer)) != -1)
                            zos.write(buffer, 0, bytes_read);
                    }

                }
            }
            // add any remaining items as NEW entries
            for (Map.Entry<String, BinaryValue> entry : binariesTable.entrySet()) {
                ZipEntry nze = new ZipEntry(entry.getKey());
                zos.putNextEntry(nze);
                entry.getValue().streamBinaryTo(zos);
            }
            zos.close();
            zis.close();

            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(baos.toByteArray()));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
            throw new XPathException("IO Exception in zip:update");
        } catch (PermissionDeniedException e) {
            logger.error(e.getMessage(), e);
            throw new XPathException("Permission denied to read the source zip");
        }
    }


    private Sequence extractEntries(XmldbURI uri) throws XPathException {
        ZipFileSource zipFileSource = new ZipFileFromDb(uri);
        ZipInputStream zis = null;

        Sequence xmlResponse = null;
        MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement( new QName( "file", ZipModule.NAMESPACE_URI, ZipModule.PREFIX ), null );
        builder.addAttribute( new QName( "href", null, null ), uri.toString() );

        try {
            zis = zipFileSource.getStream();
            ZipEntry zipEntry;
            while ((zipEntry = zis.getNextEntry()) != null) {

                if (zipEntry.isDirectory()) {
                    builder.startElement(new QName("dir",ZipModule.NAMESPACE_URI,ZipModule.PREFIX),null);
                    builder.addAttribute(new QName("name",null,null), zipEntry.toString());
                    builder.endElement();
                } else {
                    logger.debug("file: " + zipEntry.getName());
                    builder.startElement(new QName("entry",ZipModule.NAMESPACE_URI,ZipModule.PREFIX),null);
                    builder.addAttribute(new QName("name",null,null), zipEntry.toString());
                    builder.endElement();
                }
            }
        }catch(PermissionDeniedException pde) {
            logger.error(pde.getMessage(), pde);
            throw new XPathException("Permission denied to read the source zip");
        }catch(IOException ioe){
            logger.error(ioe.getMessage(), ioe);
            throw new XPathException("IO exception while reading the source zip");
        }

        builder.endElement();
        xmlResponse = (NodeValue) builder.getDocument().getDocumentElement();
        return(xmlResponse);
    }

    private Sequence createZip(Element zipFile) {
        Node child = zipFile.getFirstChild();
        logger.debug("processing zipFile: " + zipFile.getAttribute("href"));
        // if this IS the zip:entry, then the src attribute will tell us where to write the output file.
        // if it has no src
        while (child != null) {
            //Parse each of the child nodes
            if (child.getNodeType() == Node.ELEMENT_NODE) { // && child.hasChildNodes()) {
                Element e = (Element) child;
                // I need to be able to handle a dir element because that's in the SPEC
                String s = e.getLocalName();
                if (s.equals("entry")) {// process the entry by finding the content, serializing according to the attributes, and streaming into the new zip file
                    logger.debug("zip:entry name: " + e.getAttribute("name") + " src: " + e.getAttribute("src"));

                } else if (s.equals("dir")) {
                    logger.debug("zip:entry contains dir: " + e.getAttribute("name") + " src: " + e.getAttribute("src"));

                }
            }
            child = child.getNextSibling();
        }
        return( Sequence.EMPTY_SEQUENCE );
    }

    // copied from
    public interface ZipFileSource {
        public ZipInputStream getStream() throws IOException, PermissionDeniedException;

        public void close();
    }

    private class ZipFileFromDb implements ZipFileSource {
        private BinaryDocument binaryDoc = null;
        private final XmldbURI uri;

        public ZipFileFromDb(XmldbURI uri) {
            this.uri = uri;
        }

        @Override
        public ZipInputStream getStream() throws IOException, PermissionDeniedException {

            if (binaryDoc == null) {
                binaryDoc = getDoc();
            }

            return new ZipInputStream(context.getBroker().getBinaryResource(binaryDoc));
        }

        @Override
        public void close() {
            if (binaryDoc != null) {
                binaryDoc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }

        private BinaryDocument getDoc() throws PermissionDeniedException {

            DocumentImpl doc = context.getBroker().getXMLResource(uri, Lock.READ_LOCK);
            if (doc == null || doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                return null;
            }

            return (BinaryDocument) doc;
        }
    }

    // copied from AccountManagementFunction
    private String[] getPaths(final Sequence seq) {
        final String paths[] = new String[seq.getItemCount()];
        for(int i = 0; i < seq.getItemCount(); i++) {
            paths[i] = seq.itemAt(i).toString();
        }
        return paths;
    }


    private BinaryValue[] getBinaryData(final Sequence seq) {
        final BinaryValue binaries[] = new BinaryValue[seq.getItemCount()];
        for(int i = 0; i < seq.getItemCount(); i++) {
            binaries[i] = (BinaryValue) seq.itemAt(i);
        }
        return binaries;
    }
}
