package org.exist.source;


import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.security.PermissionDeniedException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
}
