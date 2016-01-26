
package org.exist.util;

import java.io.File;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public class XMLFilenameFilter implements FilenameFilter {

    public XMLFilenameFilter() {
    }

    public boolean accept(File dir, String name) {
        final MimeTable mimetab = MimeTable.getInstance();
        final MimeType mime = mimetab.getContentTypeFor(name);
        return mime != null && mime.isXMLType();
    }

    public static Predicate<Path> asPredicate() {
        final MimeTable mimetab = MimeTable.getInstance();
        return path -> {
            if(!Files.isDirectory(path)) {
                final MimeType mime = mimetab.getContentTypeFor(FileUtils.fileName(path));
                return mime != null && mime.isXMLType();
            }
            return false;
        };
    }
}
