package org.exist.http.urlrewrite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author aretter
 */
public class XQueryURLRewriteTest
{
    @Test
    public void adjustPathForSourceLookup_fullXmldbUri() {
        XQueryURLRewrite rewriter = new XQueryURLRewrite();


        String basePath = "xmldb:exist:///db/adamretter.org.uk/";
        String path = "/db/adamretter.org.uk/blog/entries/some-entry.xml?edit";

        String adjustedPath = rewriter.adjustPathForSourceLookup(basePath, path);

        assertEquals(adjustedPath, "blog/entries/some-entry.xml?edit");
    }

    @Test
    public void adjustPathForSourceLookup_dbUri() {
        XQueryURLRewrite rewriter = new XQueryURLRewrite();


        String basePath = "/";
        String path = "/db/adamretter.org.uk/blog/entries/some-entry.xml?edit";

        String adjustedPath = rewriter.adjustPathForSourceLookup(basePath, path);

        assertEquals(adjustedPath, "adamretter.org.uk/blog/entries/some-entry.xml?edit");
    }

    @Test
    public void adjustPathForSourceLookup_fsUri() {
        XQueryURLRewrite rewriter = new XQueryURLRewrite();


        String basePath = "/";
        String path = "/xquery/functions.xql";

        String adjustedPath = rewriter.adjustPathForSourceLookup(basePath, path);

        assertEquals(adjustedPath, "xquery/functions.xql");
    }
}