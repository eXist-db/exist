package org.exist.source;


import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

@RunWith(ParallelRunner.class)
public class SourceFactoryTest {

    @Test
    public void getSourceFromFile_contextAbsoluteFileUrl_locationAbsoluteUrl() throws IOException, PermissionDeniedException, URISyntaxException {
        final URL mainUrl = getClass().getResource("main.xq");
        final String contextPath = mainUrl.toString();
        final URL libraryUrl = getClass().getResource("library.xqm");
        final String location = libraryUrl.toString();

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(new java.io.File(libraryUrl.toURI()).getAbsolutePath(), source.getKey());
    }

    @Test
    public void getSourceFromFile_contextAbsoluteFile_locationAbsoluteFile() throws IOException, PermissionDeniedException, URISyntaxException {
        final URL mainUrl = getClass().getResource("main.xq");
        final String contextPath = Paths.get(mainUrl.toURI()).toAbsolutePath().toString();
        final URL libraryUrl = getClass().getResource("library.xqm");
        final String location = Paths.get(libraryUrl.toURI()).toAbsolutePath().toString();

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(new java.io.File(libraryUrl.toURI()).getAbsolutePath(), source.getKey());
    }

    @Test
    public void getSourceFromFile_contextAbsoluteFileUrl_locationRelative() throws IOException, PermissionDeniedException, URISyntaxException {
        final URL mainUrl = getClass().getResource("main.xq");
        final String contextPath = mainUrl.toString();
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(new java.io.File(getClass().getResource("library.xqm").toURI()).getAbsolutePath(), source.getKey());
    }

    @Test
    public void getSourceFromFile_contextAbsoluteFile_locationRelative() throws IOException, PermissionDeniedException, URISyntaxException {
        final URL mainUrl = getClass().getResource("main.xq");
        final String contextPath = Paths.get(mainUrl.toURI()).toAbsolutePath().toString();
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(new java.io.File(getClass().getResource("library.xqm").toURI()).getAbsolutePath(), source.getKey());
    }

    @Test
    public void getSourceFromFile_contextAbsoluteDir_locationRelative() throws IOException, PermissionDeniedException, URISyntaxException {
        final URL mainUrl = getClass().getResource("main.xq");
        final String contextPath = Paths.get(mainUrl.toURI()).getParent().toString();
        //final String contextPath = mainParent.substring(0, mainParent.lastIndexOf('/'));
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(Paths.get(getClass().getResource("library.xqm").toURI()).toString(), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextAbsoluteFileUrl_locationRelative() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source/main.xq";
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof ClassLoaderSource);
        assertEquals(getClass().getResource("library.xqm"), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextAbsoluteFileUrl_locationAbsoluteUrl() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source/main.xq";
        final String location = "resource:org/exist/source/library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof ClassLoaderSource);
        assertEquals(getClass().getResource("library.xqm"), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextAbsoluteFileUrl_locationRelativeUrl() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source/main.xq";
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof ClassLoaderSource);
        assertEquals(getClass().getResource("library.xqm"), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextAbsoluteFileUrl_locationRelativeUrl_basedOnSource() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source/main.xq";
        final String location = "library.xqm";

        final Source mainSource = SourceFactory.getSource(null, "", contextPath, false);
        assertTrue(mainSource instanceof ClassLoaderSource);

        final Source relativeSource = SourceFactory.getSource(null, ((ClassLoaderSource)mainSource).getSource(), location, false);

        assertTrue(relativeSource instanceof ClassLoaderSource);
        assertEquals(getClass().getResource(location), relativeSource.getKey());
    }

    @Test
    public void getSourceFromResource_contextFolderUrl_locationRelative() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source";
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof ClassLoaderSource);
        assertEquals(getClass().getResource("library.xqm"), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextFolderUrl_locationAbsoluteUrl() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source";
        final String location = "resource:org/exist/source/library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof ClassLoaderSource);
        assertEquals(getClass().getResource("library.xqm"), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextFolderUrl_locationRelativeUrl() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source";
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof ClassLoaderSource);
        assertEquals(getClass().getResource("library.xqm"), source.getKey());
    }

    @Test
    public void getSourceFromResource_contextFolderUrl_locationRelativeUrl_basedOnSource() throws IOException, PermissionDeniedException {
        final String contextPath = "resource:org/exist/source";
        final String location = "library.xqm";

        final Source mainSource = SourceFactory.getSource(null, "", contextPath, false);
        assertTrue(mainSource instanceof ClassLoaderSource);

        final Source relativeSource = SourceFactory.getSource(null, ((ClassLoaderSource)mainSource).getSource(), location, false);

        assertTrue(relativeSource instanceof ClassLoaderSource);
        assertEquals(getClass().getResource(location), relativeSource.getKey());
    }

    @Test
    public void getSourceFromXmldb_noContext() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "xmldb:exist:///db/library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        final LockedDocument mockLockedDoc = createMock(LockedDocument.class);
        final BinaryDocument mockBinDoc = createMock(BinaryDocument.class);
        final DocumentMetadata mockDocMetadata = createMock(DocumentMetadata.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(mockLockedDoc);
        expect(mockLockedDoc.getDocument()).andReturn(mockBinDoc);
        expect(mockBinDoc.getResourceType()).andReturn(BinaryDocument.BINARY_FILE);
        expect(mockBinDoc.getURI()).andReturn(XmldbURI.create(location));
        expect(mockBinDoc.getMetadata()).andReturn(mockDocMetadata);
        expect(mockDocMetadata.getLastModified()).andReturn(123456789l);
        /*expect*/ mockLockedDoc.close();

        replay(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertTrue(libSource instanceof DBSource);
        assertEquals(XmldbURI.create(location), libSource.getKey());

        verify(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);
    }

    @Test
    public void getSourceFromXmldb() throws IOException, PermissionDeniedException {
        final String contextPath = "xmldb:exist:///db";
        final String location = "library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        final LockedDocument mockLockedDoc = createMock(LockedDocument.class);
        final BinaryDocument mockBinDoc = createMock(BinaryDocument.class);
        final DocumentMetadata mockDocMetadata = createMock(DocumentMetadata.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(mockLockedDoc);
        expect(mockLockedDoc.getDocument()).andReturn(mockBinDoc);
        expect(mockBinDoc.getResourceType()).andReturn(BinaryDocument.BINARY_FILE);
        expect(mockBinDoc.getURI()).andReturn(XmldbURI.create(contextPath).append(location));
        expect(mockBinDoc.getMetadata()).andReturn(mockDocMetadata);
        expect(mockDocMetadata.getLastModified()).andReturn(123456789l);
        /*expect*/ mockLockedDoc.close();

        replay(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertTrue(libSource instanceof DBSource);
        assertEquals(XmldbURI.create(contextPath).append(location), libSource.getKey());

        verify(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);
    }

    @Test
    public void getNonExistentSourceFromXmldb_noContext() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "xmldb:exist:///db/library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(null);

        replay(mockBroker);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertNull(libSource);

        verify(mockBroker);
    }

    @Test
    public void getNonExistentSourceFromXmldb() throws IOException, PermissionDeniedException {
        final String contextPath = "xmldb:exist:///db";
        final String location = "library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(null);

        replay(mockBroker);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertNull(libSource);

        verify(mockBroker);
    }

    @Test
    public void getSourceFromXmldbEmbedded_noContext() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "xmldb:exist://embedded-eXist-server/db/library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        final LockedDocument mockLockedDoc = createMock(LockedDocument.class);
        final BinaryDocument mockBinDoc = createMock(BinaryDocument.class);
        final DocumentMetadata mockDocMetadata = createMock(DocumentMetadata.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(mockLockedDoc);
        expect(mockLockedDoc.getDocument()).andReturn(mockBinDoc);
        expect(mockBinDoc.getResourceType()).andReturn(BinaryDocument.BINARY_FILE);
        expect(mockBinDoc.getURI()).andReturn(XmldbURI.create(location));
        expect(mockBinDoc.getMetadata()).andReturn(mockDocMetadata);
        expect(mockDocMetadata.getLastModified()).andReturn(123456789l);
        /*expect*/ mockLockedDoc.close();

        replay(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertTrue(libSource instanceof DBSource);
        assertEquals(XmldbURI.create(location), libSource.getKey());

        verify(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);
    }

    @Test
    public void getSourceFromXmldbEmbedded() throws IOException, PermissionDeniedException {
        final String contextPath = "xmldb:exist://embedded-eXist-server/db";
        final String location = "library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        final LockedDocument mockLockedDoc = createMock(LockedDocument.class);
        final BinaryDocument mockBinDoc = createMock(BinaryDocument.class);
        final DocumentMetadata mockDocMetadata = createMock(DocumentMetadata.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(mockLockedDoc);
        expect(mockLockedDoc.getDocument()).andReturn(mockBinDoc);
        expect(mockBinDoc.getResourceType()).andReturn(BinaryDocument.BINARY_FILE);
        expect(mockBinDoc.getURI()).andReturn(XmldbURI.create(contextPath).append(location));
        expect(mockBinDoc.getMetadata()).andReturn(mockDocMetadata);
        expect(mockDocMetadata.getLastModified()).andReturn(123456789l);
        /*expect*/ mockLockedDoc.close();

        replay(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertTrue(libSource instanceof DBSource);
        assertEquals(XmldbURI.create(contextPath).append(location), libSource.getKey());

        verify(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);
    }

    @Test
    public void getNonExistentSourceFromXmldbEmbedded_noContext() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "xmldb:exist://embedded-eXist-server/db/library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(null);

        replay(mockBroker);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertNull(libSource);

        verify(mockBroker);
    }

    @Test
    public void getNonExistentSourceFromXmldbEmbedded() throws IOException, PermissionDeniedException {
        final String contextPath = "xmldb:exist://embedded-eXist-server/db";
        final String location = "library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(null);

        replay(mockBroker);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertNull(libSource);

        verify(mockBroker);
    }

    @Test
    public void getSourceFromDb() throws IOException, PermissionDeniedException {
        final String contextPath = "/db";
        final String location = "library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        final LockedDocument mockLockedDoc = createMock(LockedDocument.class);
        final BinaryDocument mockBinDoc = createMock(BinaryDocument.class);
        final DocumentMetadata mockDocMetadata = createMock(DocumentMetadata.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(mockLockedDoc);
        expect(mockLockedDoc.getDocument()).andReturn(mockBinDoc);
        expect(mockBinDoc.getResourceType()).andReturn(BinaryDocument.BINARY_FILE);
        expect(mockBinDoc.getURI()).andReturn(XmldbURI.create(contextPath).append(location));
        expect(mockBinDoc.getMetadata()).andReturn(mockDocMetadata);
        expect(mockDocMetadata.getLastModified()).andReturn(123456789l);
        /*expect*/ mockLockedDoc.close();

        replay(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertTrue(libSource instanceof DBSource);
        assertEquals(XmldbURI.create(contextPath).append(location), libSource.getKey());

        verify(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);
    }

    @Test
    public void getSourceFromDb_noContext() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "/db/library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        final LockedDocument mockLockedDoc = createMock(LockedDocument.class);
        final BinaryDocument mockBinDoc = createMock(BinaryDocument.class);
        final DocumentMetadata mockDocMetadata = createMock(DocumentMetadata.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(mockLockedDoc);
        expect(mockLockedDoc.getDocument()).andReturn(mockBinDoc);
        expect(mockBinDoc.getResourceType()).andReturn(BinaryDocument.BINARY_FILE);
        expect(mockBinDoc.getURI()).andReturn(XmldbURI.create(location));
        expect(mockBinDoc.getMetadata()).andReturn(mockDocMetadata);
        expect(mockDocMetadata.getLastModified()).andReturn(123456789l);
        /*expect*/ mockLockedDoc.close();

        replay(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertTrue(libSource instanceof DBSource);
        assertEquals(XmldbURI.create(location), libSource.getKey());

        verify(mockBroker, mockLockedDoc, mockBinDoc, mockDocMetadata);
    }

    @Test
    public void getNonExistentSourceFromDb() throws IOException, PermissionDeniedException {
        final String contextPath = "/db";
        final String location = "library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(null);

        replay(mockBroker);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertNull(libSource);

        verify(mockBroker);
    }

    @Test
    public void getNonExistentSourceFromDb_noContext() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "/db/library.xqm";

        final DBBroker mockBroker = createMock(DBBroker.class);
        expect(mockBroker.getXMLResource(anyObject(), anyObject())).andReturn(null);

        replay(mockBroker);

        final Source libSource = SourceFactory.getSource(mockBroker, contextPath, location, false);
        assertNull(libSource);

        verify(mockBroker);
    }

    @Test
    public void getSource_justFilename() throws IOException, PermissionDeniedException {
        final String contextPath = null;
        final String location = "library.xqm";

        final Source mainSource = SourceFactory.getSource(null, contextPath, location, false);
        assertNull(mainSource);
    }
}
