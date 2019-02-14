package org.exist.storage;

import org.easymock.Capture;
import org.easymock.EasyMock;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertEquals;


public class NativeBrokerTest {

    /**
     * When copying a Collection (/db/test/source) where
     * we have execute+read access and
     * which has no descendant documents or collections in it
     * to the destination /db/test/dest (which does not already exist)
     * and we have execute+write access on /db/test
     * we should be allowed to copy the Collection.
     */
    @Test
    public void copyCollection_noDescendants_toNonExistingDest_canWriteDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = null;


        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
            .addMockedMethod("getCollection")
            .addMockedMethod("getCurrentSubject")
            .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);

        //no sub-documents
        expect(srcCollection.iteratorNoLock(broker)).andReturn(Collections.emptyIterator());

        //no sub-collections
        expect(srcCollection.collectionIteratorNoLock(broker)).andReturn(Collections.emptyIterator());


        //test below

        replay(destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    /**
     * When copying a Collection (/db/test/source) where
     * we have execute+read access and
     * which has no descendant documents or collections in it,
     *
     * to the destination /db/test/dest (which does not already exist)
     * and we DO NOT have execute+write access on /db/test
     * we should NOT be allowed to copy the Collection.
     */
    @Test(expected = PermissionDeniedException.class)
    public void copyCollection_noDescendants_toNonExistingDest_cannotWriteDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = null; //EasyMock.createMock(Collection.class);

        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);


        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(false);

        //expectations for exception that should be thrown
        expect(srcCollection.getURI()).andReturn(src);
        expect(destCollection.getURI()).andReturn(dest);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(subject.getName()).andReturn("Fake user");

        //test below
        replay(subject, destCollection, destPermissions, srcCollection, srcPermissions, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        //not actually called, but here for showing intention
        verify(subject, destCollection, destPermissions, srcCollection, srcPermissions, broker);
    }

    /**
     * When copying a Collection (/db/test/source) where
     * we have execute+read access and
     * which has one descendant document (on which we have read access)
     * in it,
     *
     * to the destination /db/test/dest (which does not already exist)
     * and we have execute+write access on /db/test
     * we should be allowed to copy the Collection.
     */
    @Test
    public void copyCollection_oneSubDoc_toNonExistingDest_canWriteDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final DocumentImpl srcSubDocument = EasyMock.createStrictMock(DocumentImpl.class);
        final Permission srcSubDocumentPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = null;


        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);

        //one sub-document with READ permission
        expect(srcCollection.iteratorNoLock(broker)).andReturn(new ArrayIterator<>(srcSubDocument));
        expect(srcSubDocument.getPermissions()).andReturn(srcSubDocumentPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcSubDocumentPermissions.validate(subject, Permission.READ)).andReturn(true);

        //no sub-collections
        expect(srcCollection.collectionIteratorNoLock(broker)).andReturn(Collections.emptyIterator());

        //test below
        replay(srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    /**
     * When copying a Collection (/db/test/source) where
     * we have execute+read access and
     * which has one descendant document (on which we have read access)
     * and one descendant collection (on which we have read+execute access) in it,
     *
     * to the destination /db/test/dest (which does not already exist)
     * and we have execute+write access on /db/test
     * we should be allowed to copy the Collection.
     */
    @Ignore
    @Test
    public void copyCollection_oneSubDoc_oneSubColl_toNonExistingDest_canWriteDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final DocumentImpl srcSubDocument = EasyMock.createStrictMock(DocumentImpl.class);
        final Permission srcSubDocumentPermissions = EasyMock.createStrictMock(Permission.class);
        final XmldbURI srcSubCollectionName = XmldbURI.create("sub-collection");
        final XmldbURI srcSubCollectionUri = src.append(srcSubCollectionName);
        final Collection srcSubCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcSubCollectionPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = null;

        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);

        //one sub-document with READ permission
        expect(srcCollection.iterator(broker)).andReturn(new ArrayIterator<>(srcSubDocument));
        expect(srcSubDocument.getPermissions()).andReturn(srcSubDocumentPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcSubDocumentPermissions.validate(subject, Permission.READ)).andReturn(true);

        //one sub-collection with READ and EXECUTE permission
        expect(srcCollection.collectionIterator(broker)).andReturn(new ArrayIterator<>(srcSubCollectionName));
        expect(srcCollection.getURI()).andReturn(src);          //TODO fix?!? .once()  .anyTimes()  .times(2)
        expect(src.append(srcSubCollectionName)).andReturn(srcSubCollectionUri);
        expect(broker.getCollection(srcSubCollectionUri)).andReturn(srcSubCollection);


        /* we are now recursing on the sub-collection */
        expect(srcSubCollection.getPermissionsNoLock()).andReturn(srcSubCollectionPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcSubCollectionPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);
        expect(broker.getCollection(dest.append(newName))).andReturn(null); //no such dest collection, so return null
        expect(broker.getCollection(dest.append(newName).append(srcSubCollectionName))).andReturn(null); //no such dest sub-collection, so return null

        expect(srcSubCollection.iterator(broker)).andReturn(Collections.emptyIterator()); //no sub-sub-docs
        expect(srcSubCollection.collectionIterator(broker)).andReturn(Collections.emptyIterator()); //no sub-sub-collections


        //test below

        replay(srcSubCollectionPermissions, srcSubCollection, srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(srcSubCollectionPermissions, srcSubCollection, srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    /**
     * When copying an empty Collection (/db/test/source) where
     * we have execute+read access
     *
     * to the destination /db/test/dest (which already exists)
     * and we have execute+write access on /db/test and /db/test/dest
     * we should be allowed to copy the content of the Collection.
     */
    @Test
    public void copyCollection_noDescendants_toExistingDest_canWriteDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = EasyMock.createStrictMock(Collection.class);
        final Permission newDestPermissions = EasyMock.createStrictMock(Permission.class);


        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);
        expect(newDestCollection.getPermissionsNoLock()).andReturn(newDestPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(newDestPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);

        //no sub-documents
        expect(srcCollection.iteratorNoLock(broker)).andReturn(Collections.emptyIterator());

        //no sub-collections
        expect(srcCollection.collectionIteratorNoLock(broker)).andReturn(Collections.emptyIterator());

        //test below
        replay(newDestPermissions, newDestCollection, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(newDestPermissions, newDestCollection, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    /**
     * When copying an empty Collection (/db/test/source) where
     * we have execute+read access
     *
     * to the destination /db/test/dest (which already exists)
     * and we DO NOT have execute+write access on /db/test
     * we should NOT be allowed to copy the content of the Collection.
     */
    @Test(expected = PermissionDeniedException.class)
    public void copyCollection_noDescendants_toExistingDest_cannotWriteDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = EasyMock.createStrictMock(Collection.class);
        final Permission newDestPermissions = EasyMock.createStrictMock(Permission.class);


        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(false);

        //expectations for exception that should be thrown
        expect(srcCollection.getURI()).andReturn(src);
        expect(destCollection.getURI()).andReturn(dest);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(subject.getName()).andReturn("Fake user");

        //no sub-documents
        expect(srcCollection.iterator(broker)).andReturn(Collections.emptyIterator());

        //no sub-collections
        expect(srcCollection.collectionIterator(broker)).andReturn(Collections.emptyIterator());

        //test below
        replay(newDestPermissions, newDestCollection, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(newDestPermissions, newDestCollection, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    /**
     * When copying an empty Collection (/db/test/source) where
     * we have execute+read access
     *
     * to the destination /db/test/dest (which already exists)
     * and we have execute+write access on /db/test
     * but DO NOT have execute+write access on /db/test/dest
     * we should NOT be allowed to copy the content of the Collection.
     */
    @Test(expected = PermissionDeniedException.class)
    public void copyCollection_noDescendants_toExistingDest_cannotWriteNewDest() throws LockException, PermissionDeniedException {
        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = EasyMock.createStrictMock(Collection.class);
        final Permission newDestPermissions = EasyMock.createStrictMock(Permission.class);


        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);
        expect(newDestCollection.getPermissionsNoLock()).andReturn(newDestPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(newDestPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(false);

        //expectations for exception that should be thrown
        expect(srcCollection.getURI()).andReturn(src);
        expect(newDestCollection.getURI()).andReturn(dest.append(newName));
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(subject.getName()).andReturn("Fake user");

        //no sub-documents
        expect(srcCollection.iterator(broker)).andReturn(Collections.emptyIterator());

        //no sub-collections
        expect(srcCollection.collectionIterator(broker)).andReturn(Collections.emptyIterator());

        //test below
        replay(newDestPermissions, newDestCollection, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(newDestPermissions, newDestCollection, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }


    /**
     * When copying a Collection (/db/test/source) where
     * we have execute+read access and
     * which has one descendant document (on which we have read access)
     * in it,
     *
     * to the destination /db/test/dest (which already exists)
     * and we have execute+write access on /db/test and /db/test/dest
     * we should be allowed to copy the content of the Collection.
     */
    @Test
    public void copyCollection_oneSubDoc_toExistingDest_canWriteDest() throws LockException, PermissionDeniedException {

        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final DocumentImpl srcSubDocument = EasyMock.createStrictMock(DocumentImpl.class);
        final Permission srcSubDocumentPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = EasyMock.createStrictMock(Collection.class);
        final Permission newDestPermissions = EasyMock.createStrictMock(Permission.class);

        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);
        expect(newDestCollection.getPermissionsNoLock()).andReturn(newDestPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(newDestPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);

        //one sub-document with READ permission
        expect(srcCollection.iteratorNoLock(broker)).andReturn(new ArrayIterator<>(srcSubDocument));
        expect(srcSubDocument.getPermissions()).andReturn(srcSubDocumentPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcSubDocumentPermissions.validate(subject, Permission.READ)).andReturn(true);
        expect(newDestCollection.isEmpty(broker)).andReturn(true); //no documents in the dest collection

        //no sub-collections
        expect(srcCollection.collectionIteratorNoLock(broker)).andReturn(Collections.emptyIterator());

        //test below
        replay(newDestPermissions, newDestCollection, srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(newDestPermissions, newDestCollection, srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    /**
     * When copying a Collection (/db/test/source) where
     * we have execute+read access and
     * which has one descendant document (on which we DO NOT have read access)
     * in it,
     *
     * to the destination /db/test/dest (which already exists)
     * and we have execute+write access on /db/test and /db/test/dest
     * we should NOT be allowed to copy the content of the Collection.
     */
    @Test(expected=PermissionDeniedException.class)
    public void copyCollection_oneSubDoc_toExistingDest_cannotReadSubDoc() throws LockException, PermissionDeniedException {

        final XmldbURI src = XmldbURI.create("/db/test/source");
        final XmldbURI dest = XmldbURI.create("/db/test");
        final XmldbURI newName = XmldbURI.create("dest");

        final Collection srcCollection = EasyMock.createStrictMock(Collection.class);
        final Permission srcPermissions = EasyMock.createStrictMock(Permission.class);

        final DocumentImpl srcSubDocument = EasyMock.createStrictMock(DocumentImpl.class);
        final Permission srcSubDocumentPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection destCollection = EasyMock.createStrictMock(Collection.class);
        final Permission destPermissions = EasyMock.createStrictMock(Permission.class);

        final Collection newDestCollection = EasyMock.createStrictMock(Collection.class);
        final Permission newDestPermissions = EasyMock.createStrictMock(Permission.class);

        final NativeBroker broker = EasyMock.createMockBuilder(NativeBroker.class)
                .addMockedMethod("getCollection")
                .addMockedMethod("getCurrentSubject")
                .createStrictMock();

        final Subject subject = EasyMock.createStrictMock(Subject.class);

        //grant EXECUTE and READ permissions on the src
        expect(srcCollection.getPermissionsNoLock()).andReturn(srcPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcPermissions.validate(subject, Permission.EXECUTE | Permission.READ)).andReturn(true);

        //grant EXECUTE and WRITE permission on the dest
        expect(destCollection.getURI()).andReturn(dest);
        final Capture<XmldbURI> newDestURICapture = newCapture();
        expect(broker.getCollection(capture(newDestURICapture))).andReturn(newDestCollection);
        expect(destCollection.getPermissionsNoLock()).andReturn(destPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(destPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);
        expect(newDestCollection.getPermissionsNoLock()).andReturn(newDestPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(newDestPermissions.validate(subject, Permission.EXECUTE | Permission.WRITE)).andReturn(true);

        //one sub-document with READ permission
        expect(srcCollection.iteratorNoLock(broker)).andReturn(new ArrayIterator<>(srcSubDocument));
        expect(srcSubDocument.getPermissions()).andReturn(srcSubDocumentPermissions);
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(srcSubDocumentPermissions.validate(subject, Permission.READ)).andReturn(false);

        //expectations for exception that should be thrown
        expect(srcCollection.getURI()).andReturn(src);
        expect(srcSubDocument.getURI()).andReturn(src.append(newName).append("someSubDocument.xml"));
        expect(broker.getCurrentSubject()).andReturn(subject);
        expect(subject.getName()).andReturn("Fake user");

        //no sub-collections
        expect(srcCollection.collectionIteratorNoLock(broker)).andReturn(Collections.emptyIterator());

        //test below
        replay(newDestPermissions, newDestCollection, srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        //run the test
        broker.checkPermissionsForCopy(srcCollection, destCollection, newName);

        verify(newDestPermissions, newDestCollection, srcSubDocumentPermissions, srcSubDocument, destCollection, destPermissions, srcCollection, srcPermissions, subject, broker);

        assertEquals(dest.append(newName), newDestURICapture.getValue());
    }

    private static String expectedPath(final Path folder, final XmldbURI xmldbUri) {
        String collectionPath = xmldbUri.getCollectionPath();
        if(collectionPath.startsWith("/") || collectionPath.startsWith("\\")) {
            collectionPath = collectionPath.substring(1);
        }
        return folder.resolve(collectionPath).toAbsolutePath().toString();
    }

    class ArrayIterator<T> implements Iterator<T> {
        final Iterator<T> it;

        public ArrayIterator(final T... documents) {
            this.it = Arrays.asList(documents).iterator();
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public T next() {
            return it.next();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove is not permitted");
        }
    }

    class EmptyIterator<T> implements Iterator<T> {

        @Override
        public boolean hasNext() {
            return false;
        }

        @Override
        public T next() {
            throw new NoSuchElementException("Iterator is Empty!");
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Remove is not permitted");
        }
    }

}


