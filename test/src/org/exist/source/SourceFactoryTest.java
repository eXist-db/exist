package org.exist.source;


import org.exist.security.PermissionDeniedException;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
        final String contextPath = new File(mainUrl.toURI()).getAbsolutePath();
        final URL libraryUrl = getClass().getResource("library.xqm");
        final String location = new File(libraryUrl.toURI()).getAbsolutePath();

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
        final String contextPath = new File(mainUrl.toURI()).getAbsolutePath();
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(new java.io.File(getClass().getResource("library.xqm").toURI()).getAbsolutePath(), source.getKey());
    }

    @Test
    public void getSourceFromFile_contextAbsoluteDir_locationRelative() throws IOException, PermissionDeniedException, URISyntaxException {
        final URL mainUrl = getClass().getResource("main.xq");
        final String contextPath = new File(mainUrl.toURI()).getParentFile().toString();
        //final String contextPath = mainParent.substring(0, mainParent.lastIndexOf('/'));
        final String location = "library.xqm";

        final Source source = SourceFactory.getSource(null, contextPath, location, false);

        assertTrue(source instanceof FileSource);
        assertEquals(new File(getClass().getResource("library.xqm").toURI()).getPath(), source.getKey());
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
}
