/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 *  $Id$
 */
package org.exist.xmldb.concurrent;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.xmldb.EXistXQueryService;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Static utility methods used by the tests.
 *
 * @author wolf
 */
public class DBUtils {

    /**
     * @param elementCnt
     * @param attrCnt
     * @param wordList
     * @return File
     */
    public static Path generateXMLFile(final int elementCnt, final int attrCnt, final String[] wordList) throws Exception {
        return generateXMLFile(elementCnt, attrCnt, wordList, false);
    }

    /**
     * @param elementCnt
     * @param attrCnt
     * @param wordList
     * @param namespaces
     * @return File
     */
    public static Path generateXMLFile(final int elementCnt, final int attrCnt, final String[] wordList, final boolean namespaces) throws Exception {
        return generateXMLFile(3, elementCnt, attrCnt, wordList, namespaces);
    }

    /**
     * @param depth
     * @param elementCnt
     * @param attrCnt
     * @param wordList
     * @param namespaces
     * @return File
     */
    public static Path generateXMLFile(final int depth, final int elementCnt, final int attrCnt, final String[] wordList, final boolean namespaces) throws Exception {
        final Path file = Files.createTempFile(Thread.currentThread().getName(), ".xml");
        if (Files.exists(file) && !Files.isWritable(file)) {
            throw new IllegalArgumentException("Cannot write to output file " + file.toAbsolutePath());
        }

        try (final Writer writer = Files.newBufferedWriter(file, UTF_8)) {
            final XMLGenerator gen = new XMLGenerator(elementCnt, attrCnt, depth, wordList, namespaces);
            gen.generateXML(writer);
        }
        return file;
    }

    public static Collection addCollection(final Collection parent, final String name)
            throws XMLDBException {
        final CollectionManagementService service = getCollectionManagementService(
                parent);
        return service.createCollection(name);
    }

    public static void removeCollection(final Collection parent, final String name) throws XMLDBException {
        final CollectionManagementService service = getCollectionManagementService(
                parent);
        service.removeCollection(name);
    }

    public static CollectionManagementService getCollectionManagementService(final Collection col)
            throws XMLDBException {
        return (CollectionManagementService) col.getService(
                "CollectionManagementService", "1.0");
    }

    public static void addXMLResource(final Collection col, final String resourceId, final Path file)
            throws XMLDBException {
        if (file == null || !Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist: " + file);
        }
        final XMLResource res = (XMLResource) col.createResource(
                resourceId, "XMLResource");
        res.setContent(file);
        col.storeResource(res);
    }

    public static void addXMLResource(final Collection col, final String resourceId, final String contents)
            throws XMLDBException {
        final XMLResource res = (XMLResource) col.createResource(
                resourceId, "XMLResource");
        res.setContent(contents);
        col.storeResource(res);
    }

    public static ResourceSet query(final Collection collection, final String xpath)
            throws XMLDBException {
        final XPathQueryService service = getQueryService(collection);
        return service.query(xpath);
    }

    public static ResourceSet queryResource(final Collection collection, final String resource, final String xpath)
            throws XMLDBException {
        final XPathQueryService service = getQueryService(collection);
        return service.queryResource(resource, xpath);
    }

    public static ResourceSet xquery(final Collection collection, final String xquery)
            throws XMLDBException {
        final EXistXQueryService service = getXQueryService(collection);
        final Source source = new StringSource(xquery);
        return service.execute(source);
    }

    public static XPathQueryService getQueryService(final Collection collection)
            throws XMLDBException {
        return (XPathQueryService) collection.getService(
                "XPathQueryService", "1.0");
    }

    public static EXistXQueryService getXQueryService(final Collection collection)
            throws XMLDBException {
        return (EXistXQueryService) collection.getService(
                "XQueryService", "1.0");
    }

    public static String[] wordList() throws XMLDBException {
        final URL url = DBUtils.class.getClassLoader().getResource("uk-towns.txt");
        if (url == null) {
            throw new XMLDBException();
        }

        final String[] words = new String[100];
        try {
            try (final InputStream is = url.openStream();
                    final LineNumberReader reader = new LineNumberReader(new InputStreamReader(is))) {
                for (int i = 0; i < words.length; i++) {
                    words[i] = reader.readLine();
                }
            }
            return words;
        } catch (final IOException e) {
          throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
        }
    }
}
