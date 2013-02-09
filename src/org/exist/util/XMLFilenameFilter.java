
package org.exist.util;

import java.io.File;
import java.io.FilenameFilter;

public class XMLFilenameFilter implements FilenameFilter {

    public XMLFilenameFilter() {
    }

    public boolean accept(File dir, String name) {
        final MimeTable mimetab = MimeTable.getInstance();
        final MimeType mime = mimetab.getContentTypeFor(name);
        return mime != null && mime.isXMLType();
    }
}
