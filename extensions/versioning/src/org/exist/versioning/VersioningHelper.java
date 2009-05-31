/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.versioning;

import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;

import java.io.IOException;

public class VersioningHelper {

    private final static String GET_CURRENT_REV =
            "declare namespace v=\"http://exist-db.org/versioning\";\n" +
            "declare variable $collection external;\n" +
            "declare variable $document external;\n" +
            "max(" +
            "   for $r in collection($collection)//v:properties[v:document = $document]/v:revision\n" +
            "   return xs:long($r)" +
            ")";

    private final static StringSource GET_CURRENT_REV_SOURCE = new StringSource(GET_CURRENT_REV);

    private final static String GET_CONFLICTING_REV =
            "declare namespace v=\"http://exist-db.org/versioning\";\n" +
            "declare variable $collection external;\n" +
            "declare variable $document external;\n" +
            "declare variable $base external;\n" +
            "declare variable $key external;\n" +
            "collection($collection)//v:properties[v:document = $document]" +
            "   [v:revision > $base][v:key != $key]";

    private final static StringSource GET_CONFLICTING_REV_SOURCE = new StringSource(GET_CONFLICTING_REV);

    private final static String GET_BASE_REV_FOR_KEY =
            "declare namespace v=\"http://exist-db.org/versioning\";\n" +
            "declare variable $collection external;\n" +
            "declare variable $document external;\n" +
            "declare variable $base external;\n" +
            "declare variable $key external;\n" +
            "let $p := collection($collection)//v:properties[v:document = $document]\n" +
            "let $withKey := for $r in $p[v:revision > $base][v:key = $key] " +
            "                   order by $r/v:revision descending return $r\n" +
            "return\n" +
            "   if ($withKey) then\n" +
            "       xs:long($withKey[1]/v:revision)\n" +
            "   else\n" +
            "       xs:long($p[v:revision = $base]/v:revision)";
    
    private final static StringSource GET_BASE_REV_FOR_KEY_SOURCE = new StringSource(GET_BASE_REV_FOR_KEY);
    
    public static long getCurrentRevision(DBBroker broker, XmldbURI docPath) throws XPathException, IOException {
        String docName = docPath.lastSegment().toString();
        XmldbURI collectionPath = docPath.removeLastSegment();
        XmldbURI path = VersioningTrigger.VERSIONS_COLLECTION.append(collectionPath);
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, GET_CURRENT_REV_SOURCE);
        if(compiled == null)
                context = xquery.newContext(AccessContext.VALIDATION_INTERNAL);
            else
                context = compiled.getContext();
        context.declareVariable("collection", path.toString());
        context.declareVariable("document", docName);
        if(compiled == null)
            compiled = xquery.compile(context, GET_CURRENT_REV_SOURCE);
        try {
            Sequence s = xquery.execute(compiled, Sequence.EMPTY_SEQUENCE);
            if (s.isEmpty())
                return 0;
            IntegerValue iv = (IntegerValue) s.itemAt(0);
            return iv.getLong();
        } finally {
            pool.returnCompiledXQuery(GET_CURRENT_REV_SOURCE, compiled);
        }
    }

    public static boolean newerRevisionExists(DBBroker broker, XmldbURI docPath, long baseRev, String key) throws XPathException, IOException {
        String docName = docPath.lastSegment().toString();
        XmldbURI collectionPath = docPath.removeLastSegment();
        XmldbURI path = VersioningTrigger.VERSIONS_COLLECTION.append(collectionPath);
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, GET_CONFLICTING_REV_SOURCE);
        if(compiled == null)
            context = xquery.newContext(AccessContext.VALIDATION_INTERNAL);
        else
            context = compiled.getContext();
        context.declareVariable("collection", path.toString());
        context.declareVariable("document", docName);
        context.declareVariable("base", new IntegerValue(baseRev));
        context.declareVariable("key", key);
        if(compiled == null)
            compiled = xquery.compile(context, GET_CONFLICTING_REV_SOURCE);
        try {
            Sequence s = xquery.execute(compiled, Sequence.EMPTY_SEQUENCE);
            return !s.isEmpty();
        } finally {
            pool.returnCompiledXQuery(GET_CONFLICTING_REV_SOURCE, compiled);
        }
    }

    public static long getBaseRevision(DBBroker broker, XmldbURI docPath, long baseRev, String sessionKey) throws XPathException, IOException {
        String docName = docPath.lastSegment().toString();
        XmldbURI collectionPath = docPath.removeLastSegment();
        XmldbURI path = VersioningTrigger.VERSIONS_COLLECTION.append(collectionPath);
        XQuery xquery = broker.getXQueryService();
        XQueryPool pool = xquery.getXQueryPool();
        XQueryContext context;
        CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, GET_BASE_REV_FOR_KEY_SOURCE);
        if(compiled == null)
            context = xquery.newContext(AccessContext.VALIDATION_INTERNAL);
        else
            context = compiled.getContext();
        context.declareVariable("collection", path.toString());
        context.declareVariable("document", docName);
        context.declareVariable("base", new IntegerValue(baseRev));
        context.declareVariable("key", sessionKey);

        if(compiled == null)
            compiled = xquery.compile(context, GET_BASE_REV_FOR_KEY_SOURCE);
        try {
            Sequence s = xquery.execute(compiled, Sequence.EMPTY_SEQUENCE);
            if (s.isEmpty())
                return 0;

            IntegerValue iv = (IntegerValue) s.itemAt(0);
            return iv.getLong();
        } finally {
            pool.returnCompiledXQuery(GET_BASE_REV_FOR_KEY_SOURCE, compiled);
        }
    }
}
