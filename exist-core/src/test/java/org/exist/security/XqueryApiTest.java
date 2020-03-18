/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security;

import org.apache.commons.codec.binary.Base64;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class XqueryApiTest extends AbstractApiSecurityTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Override
    protected void createCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        final Sequence result = executeQuery(uid, pwd, "xmldb:create-collection('/db', '" + collectionName + "')");
        assertEquals("/db/" + collectionName, serialize(result));
    }

    @Override
    protected void removeCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "xmldb:remove('/db/" + collectionName + "')");
    }

    @Override
    protected void chownCol(final String collectionUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:chown(xs:anyURI('" + collectionUri + "'), '" + owner_uid + ":" + group_gid + "')");
    }

    @Override
    protected void chmodCol(final String collectionUri, final String mode, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:chmod(xs:anyURI('" + collectionUri + "'), '" + mode + "')");
    }

    @Override
    protected void chmodRes(final String resourceUri, final String mode, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:chmod(xs:anyURI('" + resourceUri + "'), '" + mode + "')");
    }

    @Override
    protected void chownRes(final String resourceUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:chown(xs:anyURI('" + resourceUri + "'), '" + owner_uid + ":" + group_gid + "')");
    }

    @Override
    protected void addCollectionUserAce(final String collectionUri, final String user_uid, final String mode, final boolean allow, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:add-user-ace(xs:anyURI('" + collectionUri + "'), '" + user_uid + "', " + (allow ? "true()" : "false()") + ", '" + mode + "')");
    }

    @Override
    protected String getXmlResourceContent(final String resourceUri, final String uid, final String pwd) throws ApiException {
        final Sequence result = executeQuery(uid, pwd, "fn:doc('" + resourceUri + "')");
        return serialize(result);
    }

    @Override
    protected void removeAccount(final String account_uid, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "if (sm:user-exists('" + account_uid + "')) then sm:remove-account('" + account_uid + "') else()");
    }

    @Override
    protected void removeGroup(final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "if (sm:group-exists('" + group_gid + "')) then sm:remove-group('" + group_gid + "') else ()");
    }

    @Override
    protected void createAccount(final String account_uid, final String account_pwd, final String group_uid, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:create-account('" + account_uid + "', '" + account_pwd + "', '" + group_uid + "', ())");
    }

    @Override
    protected void createGroup(final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery(uid, pwd, "sm:create-group('" + group_gid + "')");
    }

    @Override
    protected void createXmlResource(final String resourceUri, final String content, final String uid, final String pwd) throws ApiException {
        final int resIdx = resourceUri.lastIndexOf('/');
        final String collectionUri = resourceUri.substring(0, resIdx);
        final String resourceName = resourceUri.substring(resIdx + 1);
        executeQuery(uid, pwd, "xmldb:store('" + collectionUri + "', '" + resourceName + "', fn:parse-xml('" + content + "'))");
    }

    @Override
    protected void createBinResource(final String resourceUri, final byte[] content, final String uid, final String pwd) throws ApiException {
        final int resIdx = resourceUri.lastIndexOf('/');
        final String collectionUri = resourceUri.substring(0, resIdx);
        final String resourceName = resourceUri.substring(resIdx + 1);
        final String base64Content = Base64.encodeBase64String(content);
        executeQuery(uid, pwd, "xmldb:store-as-binary('" + collectionUri + "', '" + resourceName + "', xs:base64Binary('" + base64Content + "'))");
    }

    private Sequence executeQuery(final String uid, final String pwd, final String query) throws ApiException {
        try {
            final BrokerPool pool = server.getBrokerPool();
            final XQuery xquery = pool.getXQueryService();

            final Subject user = pool.getSecurityManager().authenticate(uid, pwd);
            try (final DBBroker broker = pool.get(Optional.of(user))) {
                return xquery.execute(broker, query, null);
            }
        } catch (final AuthenticationException | EXistException | PermissionDeniedException | XPathException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }

    private String serialize(final Sequence sequence) throws ApiException {
        try (final DBBroker broker = server.getBrokerPool().getBroker();
             final StringWriter writer = new StringWriter()) {
            final XQuerySerializer serializer = new XQuerySerializer(broker, new Properties(), writer);
            serializer.serialize(sequence);
            return writer.toString();
        } catch (final EXistException | IOException | SAXException | XPathException e) {
            throw new ApiException(e.getMessage(), e);
        }
    }
}
