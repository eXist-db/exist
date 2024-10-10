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
package org.exist.xquery.modules.compression;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.BinaryValueInputSource;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.compression.CompressionModule.functionSignature;
import static org.exist.xquery.modules.compression.CompressionModule.functionSignatures;

/**
 * Various Entry helper functions for filtering etc.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class EntryFunctions extends BasicFunction {
    private final static Logger LOG = LogManager.getLogger(EntryFunctions.class);

    private static final FunctionParameterSequenceType FS_PARAM_PATH = param("path", Type.STRING, "The path of the entry");
    private static final FunctionParameterSequenceType FS_PARAM_DATA_TYPE = param("data-type", Type.STRING, "The type of the entry, either 'directory' or 'resource'.");
    private static final FunctionParameterSequenceType FS_PARAM_PARAM = optManyParam("param", Type.ITEM, "One or more parameters.");
    private static final FunctionParameterSequenceType FS_PARAM_DATA = optParam("data", Type.ITEM, "The data of the entry in the archive");
    private static final FunctionParameterSequenceType FS_PARAM_FS_DEST_PATH = optParam("destination", Type.STRING, "A path to a directory on the filesystem where the entry should be extracted. If the path does not exist it will be created.");
    private static final FunctionParameterSequenceType FS_PARAM_DB_DEST_COLLECTION = optParam("destination", Type.STRING, "A path to a Collection in the database where the entry should be extracted. If the Collection does not exist it will be created.");

    private static final String FS_NO_FILTER_NAME = "no-filter";
    static final FunctionSignature[] FS_NO_FILTER = functionSignatures(
            FS_NO_FILTER_NAME,
            "Does not filter any entries.",
            returns(Type.BOOLEAN, "Always true, so that no entries are filtered. Parameters are ignored."),
            arities(
                    arity(
                            FS_PARAM_PATH,
                            FS_PARAM_DATA_TYPE
                    ),
                    arity(
                            FS_PARAM_PATH,
                            FS_PARAM_DATA_TYPE,
                            FS_PARAM_PARAM
                    )
            )
    );

    private static final String FS_FS_STORE_ENTRY_NAME3 = "fs-store-entry3";
    static final FunctionSignature FS_FS_STORE_ENTRY3 = functionSignature(
            FS_FS_STORE_ENTRY_NAME3,
            "Stores an entry to the filesystem. This method is only available to the DBA role. Attempts to guard against exit attacks; If an exit attack is detected then the error `compression:archive-exit-attack is raised`.",
            returns(Type.FUNCTION, "A function suitable for passing as the $entry-data#3"),
            FS_PARAM_FS_DEST_PATH
    );

    private static final String FS_FS_STORE_ENTRY_NAME4 = "fs-store-entry4";
    static final FunctionSignature FS_FS_STORE_ENTRY4 = functionSignature(
            FS_FS_STORE_ENTRY_NAME4,
            "Stores an entry to the filesystem. This method is only available to the DBA role. Attempts to guard against exit attacks; If an exit attack is detected then the error `compression:archive-exit-attack is raised`.",
            returns(Type.FUNCTION, "A function suitable for passing as the $entry-data#4"),
            FS_PARAM_FS_DEST_PATH
    );

    private static final String FS_DB_STORE_ENTRY_NAME3 = "db-store-entry3";
    static final FunctionSignature FS_DB_STORE_ENTRY3 = functionSignature(
            FS_DB_STORE_ENTRY_NAME3,
            "Stores an entry to the database. Attempts to guard against exit attacks; If an exit attack is detected then the error `compression:archive-exit-attack is raised`.",
            returns(Type.FUNCTION, "A function suitable for passing as the $entry-data#3"),
            FS_PARAM_DB_DEST_COLLECTION
    );

    private static final String FS_DB_STORE_ENTRY_NAME4 = "db-store-entry4";
    static final FunctionSignature FS_DB_STORE_ENTRY4 = functionSignature(
            FS_DB_STORE_ENTRY_NAME4,
            "Stores an entry to the database. Attempts to guard against exit attacks; If an exit attack is detected then the error `compression:archive-exit-attack is raised`.",
            returns(Type.FUNCTION, "A function suitable for passing as the $entry-data#4"),
            FS_PARAM_DB_DEST_COLLECTION
    );

    public EntryFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        return switch (getName().getLocalPart()) {
            case FS_NO_FILTER_NAME -> BooleanValue.TRUE;
            case FS_FS_STORE_ENTRY_NAME3 -> {
                checkIsDBA();
                yield fsStoreEntry3(args);
            }
            case FS_FS_STORE_ENTRY_NAME4 -> {
                checkIsDBA();
                yield fsStoreEntry4(args);
            }
            case FS_DB_STORE_ENTRY_NAME3 -> dbStoreEntry3(args);
            case FS_DB_STORE_ENTRY_NAME4 -> dbStoreEntry4(args);
            default ->
                    throw new XPathException(this, "No function: " + getName() + "#" + getSignature().getArgumentCount());
        };
    }

    private void checkIsDBA() throws XPathException {
        if(!context.getSubject().hasDbaRole()) {
            final XPathException xpe = new XPathException(this, "Permission denied, calling user '" + context.getSubject().getName() + "' must be a DBA to call this function.");
            LOG.error("Invalid user", xpe);
            throw xpe;
        }
    }

    // returns a function reference like: ($path as xs:string, $data-type as xs:string, $data as item()?) as empty-sequence()
    private FunctionReference fsStoreEntry3(final Sequence[] args) throws XPathException {
        final Path fsDest = getFile(args[0].itemAt(0).toString(), this);
        return new FunctionReference(this, new FunctionCall(context, new StoreFsFunction3(context, fsDest)));

    }

    // returns a function reference like: ($path as xs:string, $data-type as xs:string, $data as item()?, $param as item()*) as empty-sequence()
    private FunctionReference fsStoreEntry4(final Sequence[] args) throws XPathException {
        final Path fsDest = getFile(args[0].itemAt(0).toString(), this);
        return new FunctionReference(this, new FunctionCall(context, new StoreFsFunction4(context, fsDest)));
    }

    // returns a function reference like: ($path as xs:string, $data-type as xs:string, $data as item()?) as empty-sequence()
    private FunctionReference dbStoreEntry3(final Sequence[] args) throws XPathException {
        final XmldbURI destCollection = XmldbURI.create(args[0].itemAt(0).toString());
        return new FunctionReference(this, new FunctionCall(context, new StoreDbFunction3(context, destCollection)));

    }

    // returns a function reference like: ($path as xs:string, $data-type as xs:string, $data as item()?, $param as item()*) as empty-sequence()
    private FunctionReference dbStoreEntry4(final Sequence[] args) throws XPathException {
        final XmldbURI destCollection = XmldbURI.create(args[0].itemAt(0).toString());
        return new FunctionReference(this, new FunctionCall(context, new StoreDbFunction4(context, destCollection)));
    }

    private static class StoreFsFunction3 extends StoreFsFunction {
        public StoreFsFunction3(final XQueryContext context, final Path fsDest) {
            super(context, fsDest, FS_FS_STORE_ENTRY_NAME3 + "-store", FS_PARAM_PATH, FS_PARAM_DATA_TYPE, FS_PARAM_DATA);
        }
    }

    private static class StoreFsFunction4 extends StoreFsFunction {
        public StoreFsFunction4(final XQueryContext context, final Path fsDest) {
            super(context, fsDest, FS_FS_STORE_ENTRY_NAME4 + "-store", FS_PARAM_PATH, FS_PARAM_DATA_TYPE, FS_PARAM_DATA, FS_PARAM_PARAM);
        }
    }

    private static class StoreDbFunction3 extends StoreDbFunction {
        public StoreDbFunction3(final XQueryContext context, final XmldbURI destCollection) {
            super(context, destCollection, FS_DB_STORE_ENTRY_NAME3 + "-store", FS_PARAM_PATH, FS_PARAM_DATA_TYPE, FS_PARAM_DATA);
        }
    }

    private static class StoreDbFunction4 extends StoreDbFunction {
        public StoreDbFunction4(final XQueryContext context, final XmldbURI destCollection) {
            super(context, destCollection, FS_DB_STORE_ENTRY_NAME4 + "-store", FS_PARAM_PATH, FS_PARAM_DATA_TYPE, FS_PARAM_DATA, FS_PARAM_PARAM);
        }
    }

    private static abstract class StoreFsFunction extends StoreFunction {
        private final Path fsDest;

        public StoreFsFunction(final XQueryContext context, final Path fsDest, final String functionName, final FunctionParameterSequenceType... paramTypes) {
            super(context, functionName, "Stores an entry to the filesystem.", paramTypes);
            this.fsDest = fsDest;
        }

        @Override
        protected void eval(final String path, final DataType dataType, final Optional<Item> data) throws XPathException {
            final Path destPath = fsDest.resolve(path).normalize();

            if (!destPath.startsWith(fsDest)) {
                throw new XPathException(this, CompressionModule.ARCHIVE_EXIT_ATTACK, "Detected archive exit attack!");
            }

            switch (dataType) {

                case resource:
                    mkdirs(destPath.getParent());
                    if(data.isPresent()) {
                        // store the resource
                        try (final OutputStream os = new BufferedOutputStream(Files.newOutputStream(destPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
                            ((BinaryValue)data.get()).streamBinaryTo(os);
                        } catch (final IOException e) {
                            throw new XPathException(this, "Cannot serialize file. A problem occurred while serializing the binary data: " + e.getMessage(), e);
                        }
                    }
                    break;

                case directory:
                    mkdirs(destPath);
                    break;
            }
        }

        /**
         * Create the directory path if it does not exist.
         *
         * @param dir the directory path to create.
         */
        private void mkdirs(final Path dir) throws XPathException {
            try {
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
            } catch (final IOException e) {
                throw new XPathException(this, "Cannot create directory(s): " + e.getMessage(), e);
            }
        }
    }

    private static abstract class StoreDbFunction extends StoreFunction {
        private final XmldbURI destCollection;

        public StoreDbFunction(final XQueryContext context, final XmldbURI destCollection, final String functionName, final FunctionParameterSequenceType... paramTypes) {
            super(context, functionName, "Stores an entry to the filesystem.", paramTypes);
            this.destCollection = destCollection;
        }

        @Override
        protected void eval(final String path, final DataType dataType, final Optional<Item> data) throws XPathException {
            final XmldbURI destPath = destCollection.resolveCollectionPath(XmldbURI.create(path));

            if (!destPath.startsWith(destCollection)) {
                throw new XPathException(this, CompressionModule.ARCHIVE_EXIT_ATTACK, "Detected archive exit attack!");
            }

            switch (dataType) {

                case resource:
                    mkcols(destPath.removeLastSegment());
                    if (data.isPresent()) {
                        // store the resource
                        try (final Txn transaction = context.getBroker().getBrokerPool().getTransactionManager().beginTransaction()) {

                            try (final Collection collection = context.getBroker().openCollection(destPath.removeLastSegment(), Lock.LockMode.WRITE_LOCK)) {
                                final BinaryValue binaryValue = (BinaryValue) data.get();
                                final MimeType mimeType = MimeTable.getInstance().getContentTypeFor(destPath.lastSegment());
                                context.getBroker().storeDocument(transaction, destPath.lastSegment(), new BinaryValueInputSource(binaryValue), mimeType, collection);
                            }
                            transaction.commit();
                        } catch (final IOException | PermissionDeniedException | EXistException | LockException | SAXException e) {
                            throw new XPathException(this, "Cannot serialize file. A problem occurred while serializing the binary data: " + e.getMessage(), e);
                        }
                    }
                    break;

                case directory:
                    mkcols(destPath);
                    break;
            }
        }

        /**
         * Create the Collection path if it does not exist.
         *
         * @param collection The collection path to create.
         */
        private void mkcols(final XmldbURI collection) throws XPathException {
            try (final Txn transaction = context.getBroker().getBrokerPool().getTransactionManager().beginTransaction()) {
                context.getBroker().getOrCreateCollection(transaction, collection);
                transaction.commit();
            } catch (final IOException | PermissionDeniedException | TriggerException | TransactionException e) {
                throw new XPathException(this, "Cannot create Collection(s): " + e.getMessage(), e);
            }
        }
    }

    private static abstract class StoreFunction extends UserDefinedFunction {
        public StoreFunction(final XQueryContext context, final String functionName, final String description, final FunctionParameterSequenceType... paramTypes) {
            super(context, functionSignature(functionName, description, returnsNothing(), paramTypes));
        }

        @Override
        public void accept(final ExpressionVisitor visitor) {
            if (visited) {
                return;
            }
            visited = true;
        }

        @Override
        public final Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence arg1 = getCurrentArguments()[0];
            final String path = arg1.itemAt(0).getStringValue();

            final Sequence arg2 = getCurrentArguments()[1];
            final String dataType = arg2.itemAt(0).getStringValue();

            final Sequence dataSeq = getCurrentArguments()[2];
            final Optional<Item> data;
            if(!dataSeq.isEmpty()) {
                data = Optional.of(dataSeq.itemAt(0));
            } else {
                data = Optional.empty();
            }

            eval(path, DataType.valueOf(dataType), data);

            return Sequence.EMPTY_SEQUENCE;
        }

        protected abstract void eval(final String path, final DataType dataType, final Optional<Item> data) throws XPathException;
    }

    private enum DataType {
        resource,
        directory
    }

    /**
     *  Convert path (URL, file path) to a File object.
     *
     * @param path Path written as OS specific path or as URL
     * @return File object
     * @throws XPathException Thrown when the URL cannot be used.
     */
    public static Path getFile(final String path, final Expression expression) throws XPathException {
        if(path.startsWith("file:")){
            try {
                return Paths.get(new URI(path));
            } catch (Exception ex) { // catch all (URISyntaxException)
                throw new XPathException(expression, path + " is not a valid URI: '"+ ex.getMessage() +"'");
            }
        } else {
            return Paths.get(path);
        }
    }
}
