/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.file;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamSource;

import org.apache.tools.ant.DirectoryScanner;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.exist.xslt.TransformerFactoryAllocator;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

public class Sync extends BasicFunction {

    public static final String PRUNE_OPT = "prune";
    public static final String AFTER_OPT = "after";
    public static final String EXCLUDES_OPT = "excludes";

    public static final QName FileSyncElement = new QName("sync", FileModule.NAMESPACE_URI);
    public static final QName FileUpdateElement = new QName("update", FileModule.NAMESPACE_URI);
    public static final QName FileDeleteElement = new QName("delete", FileModule.NAMESPACE_URI);
    public static final QName FileErrorElement = new QName("error", FileModule.NAMESPACE_URI);

    // TODO(JL) Figure out which namespace all attributes should be in (possible breaking change)
    public static final QName FileCollectionAttribute = new QName("collection", FileModule.NAMESPACE_URI);
    public static final QName FileDirAttribute = new QName("dir", FileModule.NAMESPACE_URI);

    public static final QName FileAttribute = new QName("file", XMLConstants.NULL_NS_URI);
    public static final QName NameAttribute = new QName("name", XMLConstants.NULL_NS_URI);
    public static final QName CollectionAttribute = new QName("collection", XMLConstants.NULL_NS_URI);
    public static final QName TypeAttribute = new QName("type", XMLConstants.NULL_NS_URI);
    public static final QName ModifiedAttribute = new QName("modified", XMLConstants.NULL_NS_URI);

    public static final FunctionSignature signature =
            new FunctionSignature(
                    new QName("sync", FileModule.NAMESPACE_URI, FileModule.PREFIX),
                    "Synchronize a collection with a directory hierarchy." +
                            "This method is only available to the DBA role. ",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("collection", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "Absolute path to the collection to synchronize to disk."),
                            new FunctionParameterSequenceType("targetPath", Type.ITEM, Cardinality.EXACTLY_ONE,
                                    "The path or URI to the target directory. Relative paths resolve against EXIST_HOME."),
                            new FunctionParameterSequenceType("dateTimeOrOptionsMap", Type.ITEM, Cardinality.ZERO_OR_ONE,
                                    "Options as map(*). The available settings are:" +
                                            "\"" + PRUNE_OPT + "\": delete any file/dir that does not correspond to a doc/collection in the DB. " +
                                            "\"" + AFTER_OPT + "\": only resources modified after this date will be taken into account." +
                                            "\"" + EXCLUDES_OPT + "\": files on the file system matching any of these patterns will be left untouched." +
                                            "(deprecated) If the third parameter is of type xs:dateTime, it is the same as setting the \"" + AFTER_OPT + "\" option.")
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.EXACTLY_ONE, "A report (file:sync) which files and directories were updated (file:update) or deleted (file:delete).")
            );

    private static final Properties DEFAULT_PROPERTIES = new Properties();

    static {
        DEFAULT_PROPERTIES.put(OutputKeys.INDENT, "yes");
        DEFAULT_PROPERTIES.put(OutputKeys.OMIT_XML_DECLARATION, "no");
        DEFAULT_PROPERTIES.put(EXistOutputKeys.EXPAND_XINCLUDES, "no");
    }

    public Sync(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(this, "Function file:sync is only available to the DBA role");
        }

        final String collectionPath = args[0].getStringValue();
        final String target = args[1].getStringValue();
        final HashMap<String, Sequence> options = getOptions(args[2]);

        return startSync(target, collectionPath, options);
    }

    private HashMap<String, Sequence> getOptions(final Sequence parameter) throws XPathException {
        HashMap<String, Sequence> options = new HashMap<>();
        options.put(AFTER_OPT, Sequence.EMPTY_SEQUENCE);
        options.put(PRUNE_OPT, new BooleanValue(false));
        options.put(EXCLUDES_OPT, Sequence.EMPTY_SEQUENCE);

        if (parameter.isEmpty()) {
            return options;
        }

        final Item item = parameter.itemAt(0);

        if (item.getType() == Type.MAP) {
            final AbstractMapType optionsMap = (AbstractMapType) item;

            final Sequence seq = optionsMap.get(new StringValue(EXCLUDES_OPT));
            if (!seq.isEmpty() && seq.getItemType() != Type.STRING) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Invalid value for option \"excludes\", expected xs:string* got " +
                                Type.getTypeName(seq.getItemType()));
            }
            options.put(EXCLUDES_OPT, seq);

            checkOption(optionsMap, PRUNE_OPT, Type.BOOLEAN, options);
            checkOption(optionsMap, AFTER_OPT, Type.DATE_TIME, options);
        } else if (parameter.itemAt(0).getType() == Type.DATE_TIME) {
            // QUESTION(JL) LOG deprecation warning?
            options.put(AFTER_OPT, parameter);
        } else {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid 3rd parameter, allowed are xs:dateTime or map(*) got " + Type.getTypeName(item.getType()));
        }
        return options;
    }

    private void checkOption(
            final AbstractMapType optionsMap,
            final String name,
            final int expectedType,
            final HashMap<String, Sequence> options
    ) throws XPathException {
        final Sequence p = optionsMap.get(new StringValue(name));

        if (p.isEmpty()) {
            return; // nothing to do, continue
        }

        if (p.hasMany() || p.getItemType() != expectedType) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Invalid value for option \"" + name + "\", expected " +
                            Type.getTypeName(expectedType) + " got " +
                            Type.getTypeName(p.itemAt(0).getType()));
        }

        options.put(name, p);
    }

    private Sequence startSync(
            final String target,
            final String collectionPath,
            final HashMap<String, Sequence> options
    ) throws XPathException {
        final Date startDate = options.get(AFTER_OPT).hasOne() ? ((DateTimeValue) options.get(AFTER_OPT)).getDate() : null;

        boolean prune = ((BooleanValue) options.get(PRUNE_OPT)).getValue();

        final List<String> excludes = new ArrayList<>(Collections.emptyList());
        for (final SequenceIterator si = options.get(EXCLUDES_OPT).iterate(); si.hasNext(); ) {
            excludes.add(si.nextItem().getStringValue());
        }

        final Path p = FileModuleHelper.getFile(target);
        context.pushDocumentContext();
        final MemTreeBuilder output = context.getDocumentBuilder();
        final Path targetDir;
        try {
            if (p.isAbsolute()) {
                targetDir = p;
            } else {
                final Optional<Path> home = context.getBroker().getConfiguration().getExistHome();
                targetDir = FileUtils.resolve(home, target);
            }

            output.startDocument();
            output.startElement(Sync.FileSyncElement, null);
            output.addAttribute(Sync.FileCollectionAttribute, collectionPath);
            output.addAttribute(Sync.FileDirAttribute, targetDir.toAbsolutePath().toString());

            final String rootTargetAbsPath = targetDir.toAbsolutePath().toString();
            final String separator = rootTargetAbsPath.endsWith(File.separator) ? "" : File.separator;
            syncCollection(XmldbURI.create(collectionPath), rootTargetAbsPath + separator, targetDir, startDate, prune, excludes, output);

            output.endElement();
            output.endDocument();
        } catch (final PermissionDeniedException | LockException e) {
            throw new XPathException(this, e);
        } finally {
            context.popDocumentContext();
        }
        return output.getDocument();
    }

    private void syncCollection(
            final XmldbURI collectionPath,
            final String rootTargetAbsPath,
            final Path targetDir,
            final Date startDate,
            final boolean prune,
            final List<String> excludes,
            final MemTreeBuilder output
    ) throws PermissionDeniedException, LockException {
        final Path targetDirectory;
        try {
            targetDirectory = Files.createDirectories(targetDir);
        } catch (final IOException ioe) {
            reportError(output, "Failed to create output directory: " + targetDir.toAbsolutePath() +
                    " for collection " + collectionPath);
            return;
        }

        if (!Files.isWritable(targetDirectory)) {
            reportError(output, "Failed to write to output directory: " + targetDirectory.toAbsolutePath());
            return;
        }

        List<XmldbURI> subCollections = handleCollection(collectionPath, rootTargetAbsPath, targetDirectory, startDate, prune, excludes, output);

        for (final XmldbURI childURI : subCollections) {
            final Path childDir = targetDirectory.resolve(childURI.lastSegment().toString());
            syncCollection(collectionPath.append(childURI), rootTargetAbsPath, childDir, startDate, prune, excludes, output);
        }
    }

    private List<XmldbURI> handleCollection(
            final XmldbURI collectionPath,
            final String rootTargetAbsPath,
            final Path targetDirectory,
            final Date startDate,
            final boolean prune,
            final List<String> excludes,
            final MemTreeBuilder output
    ) throws PermissionDeniedException, LockException {
        List<XmldbURI> subCollections;
        try (final Collection collection = context.getBroker().openCollection(collectionPath, LockMode.READ_LOCK)) {
            if (collection == null) {
                reportError(output, "Collection not found: " + collectionPath);
                return Collections.emptyList();
            }

            if (prune) {
                pruneCollectionEntries(collection, rootTargetAbsPath, targetDirectory, excludes, output);
            }

            for (final Iterator<DocumentImpl> i = collection.iterator(context.getBroker()); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
                final Path targetFile = targetDirectory.resolve(doc.getFileURI().toASCIIString());
                saveFile(targetFile, doc, startDate, output);
            }

            subCollections = new ArrayList<>(collection.getChildCollectionCount(context.getBroker()));
            for (final Iterator<XmldbURI> i = collection.collectionIterator(context.getBroker()); i.hasNext(); ) {
                subCollections.add(i.next());
            }
        }
        return subCollections;
    }

    private void pruneCollectionEntries(
            final Collection collection,
            final String rootTargetAbsPath,
            final Path targetDir,
            final List<String> excludes,
            final MemTreeBuilder output) {
        try (final Stream<Path> fileStream = Files.walk(targetDir, 1)) {
            fileStream.forEach(path -> {
                try {
                    // guard against deletion of output folder
                    if (rootTargetAbsPath.startsWith(path.toString())) {
                        return;
                    }

                    if (isExcludedPath(rootTargetAbsPath, path, excludes)) {
                        return;
                    }

                    final String fileName = path.getFileName().toString();
                    final XmldbURI dbname = XmldbURI.xmldbUriFor(fileName);
                    final String currentCollection = collection.getURI().getCollectionPath();

                    if (collection.hasDocument(context.getBroker(), dbname)
                            || collection.hasChildCollection(context.getBroker(), dbname)
                            || currentCollection.endsWith("/" + fileName)) {
                        return;
                    }

                    // handle non-empty directories
                    if (Files.isDirectory(path)) {
                        deleteWithExcludes(rootTargetAbsPath, path, excludes, output);
                    } else {
                        Files.deleteIfExists(path);
                        // reporting
                        output.startElement(Sync.FileDeleteElement, null);
                        output.addAttribute(Sync.FileAttribute, path.toAbsolutePath().toString());
                        output.addAttribute(Sync.NameAttribute, fileName);
                        output.endElement();
                    }

                } catch (IOException | URISyntaxException
                        | PermissionDeniedException | LockException e) {
                    reportError(output, e.getMessage());
                }
            });
        } catch (IOException e) {
            reportError(output, e.getMessage());
        }
    }

    private void saveFile(final Path targetFile, final DocumentImpl doc, final Date startDate, final MemTreeBuilder output) throws LockException {
        try (final ManagedLock<MultiLock[]> lock = context.getBroker().getBrokerPool().getLockManager().acquireDocumentReadLock(doc.getURI())) {
            if (startDate == null || (
                    doc.getLastModified() > startDate.getTime() && (
                            !Files.exists(targetFile) ||
                                    Files.getLastModifiedTime(targetFile).compareTo(FileTime.fromMillis(doc.getLastModified())) >= 0
                    ))) {
                output.startElement(Sync.FileUpdateElement, null);
                output.addAttribute(Sync.FileAttribute, targetFile.toAbsolutePath().toString());
                output.addAttribute(Sync.NameAttribute, doc.getFileURI().toString());
                output.addAttribute(Sync.CollectionAttribute, doc.getCollection().getURI().toString());
                output.addAttribute(Sync.ModifiedAttribute, new DateTimeValue(new Date(doc.getLastModified())).getStringValue());

                if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                    output.addAttribute(Sync.TypeAttribute, "binary");
                    output.endElement();
                    saveBinary(targetFile, (BinaryDocument) doc, output);
                } else {
                    output.addAttribute(Sync.TypeAttribute, "xml");
                    output.endElement();
                    saveXML(targetFile, doc, output);
                }
            }
        } catch (final XPathException e) {
            reportError(output, e.getMessage());
        } catch (final IOException e) {
            reportError(output, "IO error while saving file: " + targetFile.toAbsolutePath().toString());
        }
    }

    private void saveXML(final Path targetFile, final DocumentImpl doc, final MemTreeBuilder output) throws IOException {
        final SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        try {
            boolean isRepoXML = Files.exists(targetFile) && FileUtils.fileName(targetFile).equals("repo.xml");

            if (isRepoXML) {
                processRepoDesc(targetFile, doc, sax, output);
            } else {
                final Serializer serializer = context.getBroker().borrowSerializer();
                try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(targetFile)), StandardCharsets.UTF_8)) {
                    sax.setOutput(writer, DEFAULT_PROPERTIES);
                    serializer.setProperties(DEFAULT_PROPERTIES);

                    serializer.setSAXHandlers(sax, sax);
                    serializer.toSAX(doc);
                } finally {
                    context.getBroker().returnSerializer(serializer);
                }
            }
        } catch (final SAXException e) {
            reportError(output, "SAX exception while saving file " + targetFile.toAbsolutePath().toString() + ": " + e.getMessage());
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

            final Serializer serializer = context.getBroker().borrowSerializer();

            try (final Writer writer = new OutputStreamWriter(new BufferedOutputStream(Files.newOutputStream(targetFile)), StandardCharsets.UTF_8)) {
                sax.setOutput(writer, DEFAULT_PROPERTIES);

                final StreamSource stylesource = new StreamSource(Sync.class.getResourceAsStream("repo.xsl"));

                final SAXTransformerFactory factory = TransformerFactoryAllocator.getTransformerFactory(context.getBroker().getBrokerPool());
                final TransformerHandler handler = factory.newTransformerHandler(stylesource);
                handler.getTransformer().setParameter("original", original.getDocumentElement());
                handler.setResult(new SAXResult(sax));

                serializer.reset();
                serializer.setProperties(DEFAULT_PROPERTIES);
                serializer.setSAXHandlers(handler, handler);

                serializer.toSAX(doc);
            } finally {
                context.getBroker().returnSerializer(serializer);
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

    private void saveBinary(final Path targetFile, final BinaryDocument binary, final MemTreeBuilder output) {
        try (final InputStream is = context.getBroker().getBinaryResource(binary)) {
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (final Exception e) {
            reportError(output, e.getMessage());
        }
    }

    private void reportError(final MemTreeBuilder output, final String msg) {
        output.startElement(Sync.FileErrorElement, null);
        output.characters(msg);
        output.endElement();
    }

    /**
     * We need to convert to a relative path in relation to rootTargetAbsPath,
     * as all the exclusion patterns are relative to rootTargetAbsPath.
     *
     * @param rootTargetAbsPath the root target (abs)path
     * @param path              (abs)path to check for being excluded. Should be subdir of rootTargetAbsPath
     * @param excludes          exclude patterns (in the convention of DirectoryScanner.match)
     * @return true if the (rel)path in question is matched by some of the exclusion patterns
     */
    private static boolean isExcludedPath(String rootTargetAbsPath, Path path, List<String> excludes) {
        if (excludes.isEmpty()) {
            return false;
        }
        // root folder cannot be excluded
        // path will then also be one character shorter than rootTargetApsPath
        // and throw when attempting to construct the relative path
        if (rootTargetAbsPath.startsWith(path.toString())) {
            return false;
        }

        final String absPath = path.toAbsolutePath().toString();
        final String relPath = absPath.substring(rootTargetAbsPath.length());
        final String normalizedPath = relPath.startsWith(File.separator)
                ? relPath.substring(File.separator.length())
                : relPath;

        return matchAny(excludes, normalizedPath);
    }

    /**
     * Check if any of the patterns matches the path.
     */
    public static boolean matchAny(final Iterable<String> patterns, final String path) {
        for (final String pattern : patterns) {
            if (DirectoryScanner.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private static void deleteWithExcludes(String root, final Path path, List<String> excludes, final MemTreeBuilder output) throws IOException {
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new DeleteDirWithExcludesVisitor(root, excludes, output));
        } else {
            Files.deleteIfExists(path);
        }
    }

    private static class DeleteDirWithExcludesVisitor extends SimpleFileVisitor<Path> {

        private final List<String> excludes;
        private final String root;
        private final MemTreeBuilder output;
        private boolean hasExcludedChildren = false;

        public DeleteDirWithExcludesVisitor(final String root, final List<String> excludes, final MemTreeBuilder output) {
            this.output = output;
            this.excludes = excludes;
            this.root = root;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
            if (isExcludedPath(root, dir, excludes)) {
                hasExcludedChildren = true;
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
            if (isExcludedPath(root, file, excludes)) {
                hasExcludedChildren = true;
                return FileVisitResult.CONTINUE;
            }
            Files.deleteIfExists(file);

            output.startElement(Sync.FileDeleteElement, null);
            output.addAttribute(Sync.FileAttribute, file.toAbsolutePath().toString());
            output.addAttribute(Sync.NameAttribute, file.getFileName().toString());
            output.endElement();

            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            if (exc != null) {
                throw exc;
            }
            // deletion would fail due to non-empty directory
            if (hasExcludedChildren) {
                return FileVisitResult.CONTINUE;
            }
            Files.deleteIfExists(dir);

            output.startElement(Sync.FileDeleteElement, null);
            output.addAttribute(Sync.FileAttribute, dir.toAbsolutePath().toString());
            output.addAttribute(Sync.NameAttribute, dir.getFileName().toString());
            output.endElement();

            return FileVisitResult.CONTINUE;
        }
    }
}
