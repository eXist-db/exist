/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.storage;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.evolvedbinary.j8fu.tuple.Tuple3;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A fluent lambda API for working
 * with Documents and Collections.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FluentBrokerAPI {

    /**
     * Convenience function for constructing an {@link XmldbURI}.
     *
     * @param uri a string expressing a URI
     *
     * @return an XmldbURI
     */
    public static XmldbURI uri(final String uri) {
        return XmldbURI.create(uri);
    }

    /**
     * Creates a builder which can be used
     * for describing a series of operations
     * to be carried out with a broker
     *
     * @param brokerPool The broker pool to use brokers from
     *
     * @return a builder
     */
    public static FluentBrokerAPIBuilder builder(final BrokerPool brokerPool) {
        return new FluentBrokerAPIBuilder(brokerPool);
    }

    public static class FluentBrokerAPIBuilder {
        private final BrokerPool brokerPool;

        private FluentBrokerAPIBuilder(final BrokerPool brokerPool) {
            this.brokerPool = brokerPool;
        }

        /**
         * A Collection on which to perform operations.
         *
         * @param collectionUri The URI of the Collection.
         * @param collectionLockMode The mode under which the Collection should be locked.
         *
         * @return a builder
         */
        public FluentBrokerAPIBuilder_Col1 withCollection(final XmldbURI collectionUri, final LockMode collectionLockMode) {
            return new FluentBrokerAPIBuilder_Col1(collectionUri, collectionLockMode);
        }

//        public FluentBrokerAPIBuilder_Col1 withCollections(final Tuple2<XmldbURI, LockMode>... collectionsAndLockModes) {
//            return new FluentBrokerAPIBuilder_ColN(collectionsAndLockModes);
//        }

        public class FluentBrokerAPIBuilder_Col1 {
            private final XmldbURI collectionUri;
            private final LockMode collectionLockMode;

            private FluentBrokerAPIBuilder_Col1(final XmldbURI collectionUri, final LockMode collectionLockMode) {
                this.collectionUri = collectionUri;
                this.collectionLockMode = collectionLockMode;
            }

            /**
             * An operation to perform on a Collection.
             *
             * The operation will be executed after the Collection is retrieved and locked.
             *
             * @param <CR> The return type of the {@code collectionOp}
             * @param collectionOp The function to execute against the Collection
             *
             * @return a builder.
             */
            public <CR> FluentBrokerAPIBuilder_Col1_Exec<CR> execute(final Function<Collection, CR> collectionOp) {
                return new FluentBrokerAPIBuilder_Col1_Exec<>(collectionOp);
            }

            /**
             * A Document within the Collection on which to perform an operation.
             *
             * @param documentLookupFun A function which given a Collection, returns a tuple of Document Name and Lock Mode
             *
             * @return a builder.
             */
            public FluentBrokerAPIBuilder_Col1_NoExec_Doc1 withDocument(final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun) {
                return new FluentBrokerAPIBuilder_Col1_NoExec_Doc1(documentLookupFun);
            }

            public class FluentBrokerAPIBuilder_Col1_Exec<CR> {
                private Function<Collection, CR> collectionOp;

                private FluentBrokerAPIBuilder_Col1_Exec(final Function<Collection, CR> collectionOp) {
                    this.collectionOp = collectionOp;
                }

                /**
                 * A Document within the Collection on which to perform an operation.
                 *
                 * @param documentLookupFun A function which given a Collection, returns a tuple of Document Name and Lock Mode
                 *
                 * @return a builder.
                 */
                public FluentBrokerAPIBuilder_Col1_Exec_Doc1 withDocument(final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun) {
                    return new FluentBrokerAPIBuilder_Col1_Exec_Doc1(documentLookupFun);
                }

                /**
                 * Executes the Collection operation and returns the result.
                 *
                 * @throws PermissionDeniedException in case user does not have sufficent rights
                 * @throws LockException in case the database is locked
                 * @throws EXistException in case of an eXist-db error
                 * @return The result of the Collection operation.
                 */
                public CR doAll() throws PermissionDeniedException, LockException, EXistException {
                    final Tuple3<Optional<CR>, Optional<Void>, Optional<Void>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.of(collectionOp), null, Optional.empty(), Optional.empty());
                    return result._1.get();
                }

                public class FluentBrokerAPIBuilder_Col1_Exec_Doc1 {
                    private final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun;

                    private FluentBrokerAPIBuilder_Col1_Exec_Doc1(final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun) {
                        this.documentLookupFun = documentLookupFun;
                    }

                    /**
                     * An operation to perform on a Collection and Document.
                     *
                     * The operation will be executed after both the Collection and Document are retrieved and locked.
                     *
                     * @param <CDR> The return type of the {@code collectionDocumentOp}
                     * @param collectionDocumentOp The function to execute against the Collection and Document
                     *
                     * @return a builder.
                     */
                    public <CDR> FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec<CDR> execute(final BiFunction<Collection, DocumentImpl, CDR> collectionDocumentOp) {
                        return new FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec<>(collectionDocumentOp);
                    }

                    /**
                     * Releases the Collection.
                     *
                     * @return a builder.
                     */
                    public FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec withoutCollection() {
                        return new FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec();
                    }

                    public class FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec<CDR> {
                        private final BiFunction<Collection, DocumentImpl, CDR> collectionDocumentOp;

                        private FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec(final BiFunction<Collection, DocumentImpl, CDR> collectionDocumentOp) {
                            this.collectionDocumentOp = collectionDocumentOp;
                        }

                        /**
                         * Releases the Collection.
                         *
                         * @return a builder.
                         */
                        public FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1 withoutCollection() {
                            return new FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1();
                        }

                        /**
                         * Executes the Collection operation, the Collection and Document operation, and returns the results.
                         *
                         * @return A tuple, where the first entry is the result of the Collection operation,
                         *     and the second entry is the result of the Collection and Document operation.
                         *
                         * @throws PermissionDeniedException in case user does not have sufficent rights
                         * @throws LockException in case the database is locked
                         * @throws EXistException in case of an eXist-db error

                         */
                        public Tuple2<CR, CDR> doAll() throws PermissionDeniedException, LockException, EXistException {
                            final Tuple3<Optional<CR>, Optional<CDR>, Optional<Void>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.of(collectionOp), documentLookupFun, Optional.of(collectionDocumentOp), Optional.empty());
                            return new Tuple2<>(result._1.get(), result._2.get());
                        }

                        public class FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1 {
                            private FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1() {}

                            /**
                             * An operation to perform on a Document from the Collection.
                             *
                             * The operation will be executed after the Document is retrieved and locked, and after the Collection was released.
                             *
                             * @param <DR> The return type of the {@code documentOp}
                             * @param documentOp The function to execute against the Document
                             *
                             * @return a builder.
                             */
                            public <DR> FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1_Exec<DR> execute(final Function<DocumentImpl, DR> documentOp) {
                                return new FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1_Exec<>(documentOp);
                            }
                        }

                        public class FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1_Exec<DR> {
                            private final Function<DocumentImpl, DR> documentOp;

                            private FluentBrokerAPIBuilder_Col1_Exec_Doc1_Exec_Doc1_Exec(final Function<DocumentImpl, DR> documentOp) {
                                this.documentOp = documentOp;
                            }

                            /**
                             * Executes the Collection operation, the Collection and Document operation, the Document Operation, and returns the results.
                             *
                             * @return A triple, where the first entry is the result of the Collection operation,
                             *     the second entry is the result of the Collection and Document operation,
                             *     and the third entry is the result of the Document operation.
                             * @throws PermissionDeniedException in case user does not have sufficent rights
                             * @throws LockException in case the database is locked
                             * @throws EXistException in case of an eXist-db error

                             */
                            public Tuple3<CR, CDR, DR> doAll() throws PermissionDeniedException, LockException, EXistException {
                                final Tuple3<Optional<CR>, Optional<CDR>, Optional<DR>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.of(collectionOp), documentLookupFun, Optional.of(collectionDocumentOp), Optional.of(documentOp));
                                return new Tuple3<>(result._1.get(), result._2.get(), result._3.get());
                            }
                        }
                    }

                    public class FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec {
                        private FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec() {}

                        /**
                         * An operation to perform on a Document from the Collection.
                         *
                         * The operation will be executed after the Document is retrieved and locked, and after the Collection was released.
                         *
                         * @param <DR> The return type of the {@code documentOp}
                         * @param documentOp The function to execute against the Document
                         *
                         * @return a builder.
                         */
                        public <DR> FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec_Exec<DR> execute(final Function<DocumentImpl, DR> documentOp) {
                            return new FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec_Exec<>(documentOp);
                        }

                        /**
                         * Executes the Collection operation and returns the result.
                         *
                         * @return The result of the Collection operation.
                         * @throws PermissionDeniedException if user has not sufficient rights
                         * @throws LockException in response to an lock error
                         * @throws EXistException generic eXist-db Exception
                         */
                        public CR doAll() throws PermissionDeniedException, LockException, EXistException {
                            final Tuple3<Optional<CR>, Optional<Void>, Optional<Void>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.of(collectionOp), documentLookupFun, Optional.empty(), Optional.empty());
                            return result._1.get();
                        }

                        public class FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec_Exec<DR> {
                            private final Function<DocumentImpl, DR> documentOp;

                            private FluentBrokerAPIBuilder_Col1_Exec_Doc1_NoExec_Exec(final Function<DocumentImpl, DR> documentOp) {
                                this.documentOp = documentOp;
                            }

                            /**
                             * Executes the Collection operation, the Document operation, and returns the results.
                             *
                             * @return A tuple, where the first entry is the result of the Collection operation,
                             *     and the second entry is the result of the Document operation.
                             * @throws PermissionDeniedException if user has not sufficient rights
                             * @throws LockException in response to an lock error
                             * @throws EXistException generic eXist-db Exception
                             */
                            public Tuple2<CR, DR> doAll() throws PermissionDeniedException, LockException, EXistException {
                                final Tuple3<Optional<CR>, Optional<Void>, Optional<DR>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.of(collectionOp), documentLookupFun, Optional.empty(), Optional.of(documentOp));
                                return new Tuple2<>(result._1.get(), result._3.get());
                            }
                        }
                    }
                }
            }

            public class FluentBrokerAPIBuilder_Col1_NoExec_Doc1 {
                private final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun;

                private FluentBrokerAPIBuilder_Col1_NoExec_Doc1(final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun) {
                    this.documentLookupFun = documentLookupFun;
                }

                /**
                 * An operation to perform on a Collection and Document.
                 *
                 * The operation will be executed after both the Collection and Document are retrieved and locked.
                 *
                 * @param <CDR> The return type of the {@code collectionDocumentOp}
                 * @param collectionDocumentOp The function to execute against the Collection and Document
                 *
                 * @return a builder.
                 */
                public <CDR> FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec<CDR> execute(final BiFunction<Collection, DocumentImpl, CDR> collectionDocumentOp) {
                    return new FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec<>(collectionDocumentOp);
                }

                /**
                 * Releases the Collection.
                 *
                 * @return a builder.
                 */
                public FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec withoutCollection() {
                    return new FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec();
                }

                public class FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec<CDR> {
                    private final BiFunction<Collection, DocumentImpl, CDR> collectionDocumentOp;

                    private FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec(final BiFunction<Collection, DocumentImpl, CDR> collectionDocumentOp) {
                        this.collectionDocumentOp = collectionDocumentOp;
                    }

                    /**
                     * Releases the Collection.
                     *
                     * @return a builder.
                     */
                    public FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec withoutCollection() {
                        return new FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec();
                    }

                    /**
                     * Executes the Collection and Document operation and returns the result.
                     *
                     * @return The result of the Collection and Document operation.
                     * @throws PermissionDeniedException if user has not sufficient rights
                     * @throws LockException in response to an lock error
                     * @throws EXistException generic eXist-db Exception
                     */
                    public CDR doAll() throws PermissionDeniedException, LockException, EXistException {
                        final Tuple3<Optional<Void>, Optional<CDR>, Optional<Void>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.empty(), documentLookupFun, Optional.of(collectionDocumentOp), Optional.empty());
                        return result._2.get();
                    }

                    public class FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec {
                        private FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec() {}

                        /**
                         * An operation to perform on a Document from the Collection.
                         *
                         * The operation will be executed after the Document is retrieved and locked, and after the Collection was released.
                         *
                         * @param <DR> The return type of the {@code documentOp}
                         * @param documentOp The function to execute against the Document
                         *
                         * @return a builder.
                         */
                        public <DR> FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec_Exec<DR> execute(final Function<DocumentImpl, DR> documentOp) {
                            return new FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec_Exec<>(documentOp);
                        }
                    }

                    public class FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec_Exec<DR> {
                        private final Function<DocumentImpl, DR> documentOp;

                        private FluentBrokerAPIBuilder_Col1_NoExec_Doc1_Exec_NoExec_Exec(final Function<DocumentImpl, DR> documentOp) {
                            this.documentOp = documentOp;
                        }


                        /**
                         * Executes the Collection and Document operation, the Document operation, and returns the results.
                         *
                         * @return A tuple, where the first entry is the result of the Collection and Document operation,
                         *     and the second entry is the result of the Document operation.
                         * @throws PermissionDeniedException if user has not sufficient rights
                         * @throws LockException in response to an lock error
                         * @throws EXistException generic eXist-db Exception
                         */
                        public Tuple2<CDR, DR> doAll() throws PermissionDeniedException, LockException, EXistException {
                            final Tuple3<Optional<Void>, Optional<CDR>, Optional<DR>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.empty(), documentLookupFun, Optional.of(collectionDocumentOp), Optional.of(documentOp));
                            return new Tuple2<>(result._2.get(), result._3.get());
                        }
                    }
                }

                public class FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec {
                    private FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec() {}

                    /**
                     * An operation to perform on a Document from the Collection.
                     *
                     * The operation will be executed after the Document is retrieved and locked, and after the Collection was released.
                     *
                     * @param <DR> The return type of the {@code documentOp}
                     * @param documentOp The function to execute against the Document
                     *
                     * @return a builder.
                     */
                    public <DR> FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec_Exec<DR> execute(final Function<DocumentImpl, DR> documentOp) {
                        return new FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec_Exec<>(documentOp);
                    }

                    public class FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec_Exec<DR> {
                        private final Function<DocumentImpl, DR> documentOp;

                        private FluentBrokerAPIBuilder_Col1_NoExec_Doc1_NoExec_Exec(final Function<DocumentImpl, DR> documentOp) {
                            this.documentOp = documentOp;
                        }

                        /**
                         * Executes the Document operation and returns the result.
                         *
                         * @return The result of the Document operation.
                         * @throws PermissionDeniedException if user has not sufficient rights
                         * @throws LockException in response to an lock error
                         * @throws EXistException generic eXist-db Exception
                         */
                        public DR doAll() throws PermissionDeniedException, LockException, EXistException {
                            final Tuple3<Optional<Void>, Optional<Void>, Optional<DR>> result = FluentBrokerAPIBuilder.this.doAll(collectionUri, collectionLockMode, Optional.empty(), documentLookupFun, Optional.empty(), Optional.of(documentOp));
                            return result._3.get();
                        }
                    }
                }
            }
        }

        private <CR, CDR, DR> Tuple3<Optional<CR>, Optional<CDR>, Optional<DR>> doAll(
                final XmldbURI collectionUri, final LockMode collectionLockMode,
                final Optional<Function<Collection, CR>> collectionFun,
                @Nullable final Function<Collection, Tuple2<XmldbURI, LockMode>> documentLookupFun,
                final Optional<BiFunction<Collection, DocumentImpl, CDR>> collectionDocumentFun,
                final Optional<Function<DocumentImpl, DR>> documentFun) throws EXistException, PermissionDeniedException, LockException {

            final Optional<CR> collectionFunResult;
            final Optional<CDR> collectionDocumentFunResult;
            final Optional<DR> documentFunResult;

            try(final DBBroker broker = brokerPool.getBroker()) {

                Collection collection = null;
                try {
                    collection = broker.openCollection(collectionUri, collectionLockMode);

                    if(collection == null) {
                        throw new EXistException("No such Collection: " + collectionUri);
                    }

                    final Collection c = collection;    // needed final for closures
                    collectionFunResult = collectionFun.map(cf -> cf.apply(c));

                    if(collectionDocumentFun.isPresent() || documentFun.isPresent()) {

                        final Tuple2<XmldbURI, LockMode> docAccess = documentLookupFun.apply(collection);

                        try(final LockedDocument lockedDocument = collection.getDocumentWithLock(broker, docAccess._1, docAccess._2)) {
                            final DocumentImpl document = lockedDocument.getDocument();

                            collectionDocumentFunResult = collectionDocumentFun.map(cdf -> cdf.apply(c, document));

                            // release the Collection lock early
                            collection.close();
                            collection = null;  // signal closed

                            documentFunResult = documentFun.map(df -> df.apply(document));
                        }
                    } else {
                        collectionDocumentFunResult = Optional.empty();
                        documentFunResult = Optional.empty();
                    }
                } finally {
                    // catch-all to close the collection in case of an exception and it hasn't been closed
                    if(collection != null) {
                        collection.close();
                        collection = null;
                    }
                }
            }

            return new Tuple3<>(collectionFunResult, collectionDocumentFunResult, documentFunResult);
        }



//        public class FluentBrokerAPIBuilder_ColN {
//            private final Tuple2<XmldbURI, LockMode> collectionsAndLockModes[];
//
//            private FluentBrokerAPIBuilder_ColN(final Tuple2<XmldbURI, LockMode>... collectionsAndLockModes) {
//                this.collectionsAndLockModes = collectionsAndLockModes;
//            }
//
//            public Object[] execute(final Function<Collection, Object>... collectionOps) {
//                if(collectionsAndLockModes.length != collectionOps.length) {
//                    throw new IllegalStateException();
//                }
//            }
//        }
    }
}
