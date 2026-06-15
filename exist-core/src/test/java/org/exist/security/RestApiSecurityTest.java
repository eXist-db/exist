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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

import org.exist.http.AbstractHttpTest;
import org.exist.http.AbstractHttpTest.HttpResponseResult;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RestApiSecurityTest extends AbstractApiSecurityTest {

    @ClassRule
    public static ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static String baseUri = "/db";

    private static String getServerUri() {
        return "http://localhost:" + existWebServer.getPort() + "/rest";
    }

    @Override
    protected void createCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        executeQuery("xmldb:create-collection('/db', '" + collectionName + "')", uid, pwd);
    }

    @Override
    protected void removeCol(final String collectionName, final String uid, final String pwd) throws ApiException {
        final String collectionUri = getServerUri() + baseUri + "/" + collectionName;

        try {
            final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(collectionUri), uid, pwd)
                    .DELETE()
                    .build();
            final HttpResponseResult result = AbstractHttpTest.executeForStatusAndBody(newHttpClient(), request);
            if (result.statusCode() != HTTP_OK) {
                throw new ApiException("Could not remove collection: " + collectionUri + ". " + result.body());
            }
        } catch (final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    @Override
    protected void chownCol(final String collectionUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chown(xs:anyURI('" + collectionUri + "'), '" + owner_uid + "')", uid, pwd);
        executeQuery("sm:chgrp(xs:anyURI('" + collectionUri + "'), '" + group_gid + "')", uid, pwd);
    }

    @Override
    protected void chmodCol(final String collectionUri, final String mode, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chmod(xs:anyURI('" + collectionUri + "'), '" + mode + "')", uid, pwd);
    }

    @Override
    protected void chmodRes(final String resourceUri, final String mode, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chmod(xs:anyURI('" + resourceUri + "'), '" + mode + "')", uid, pwd);
    }

    @Override
    protected void chownRes(final String resourceUri, final String owner_uid, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:chown(xs:anyURI('" + resourceUri + "'), '" + owner_uid + "')", uid, pwd);
        executeQuery("sm:chgrp(xs:anyURI('" + resourceUri + "'), '" + group_gid + "')", uid, pwd);
    }

    @Override
    protected void addCollectionUserAce(final String collectionUri, final String user_uid, final String mode, final boolean allow, final String uid, final String pwd) throws ApiException {
        final String query = "sm:add-user-ace(xs:anyURI('" + collectionUri + "'), '" + user_uid + "', " + (allow ? "true()" : "false()") + ", '" + mode + "')";
        executeQuery(query, uid, pwd);
    }

    @Override
    protected String getXmlResourceContent(final String resourceUri, final String uid, final String pwd) throws ApiException {
        try {
            final String uri = getServerUri() + resourceUri;
            final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(uri), uid, pwd)
                    .GET()
                    .build();
            final HttpResponseResult result = AbstractHttpTest.executeForStatusAndBody(newHttpClient(), request);
            if (result.statusCode() != HTTP_OK) {
                throw new ApiException("Could not get XML resource from uri: " + resourceUri + ". " + result.body());
            } else {
                return result.body();
            }
        } catch (final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    @Override
    protected void removeAccount(final String account_uid, final String uid, final String pwd) throws ApiException {
        executeQuery("if(sm:user-exists('" + account_uid + "'))then sm:remove-account('" + account_uid + "') else()", uid, pwd);
    }

    @Override
    protected void removeGroup(final String group_uid, final String uid, final String pwd) throws ApiException {
        executeQuery("if(sm:group-exists('" + group_uid + "'))then sm:remove-group('" + group_uid + "') else()", uid, pwd);
    }

    @Override
    protected void createAccount(final String account_uid, final String account_pwd, final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:create-account('" + account_uid + "', '" + account_pwd + "', ('" + group_gid + "'))", uid, pwd);
    }

    @Override
    protected void createGroup(final String group_gid, final String uid, final String pwd) throws ApiException {
        executeQuery("sm:create-group('" + group_gid + "', '" + uid + "', '" + group_gid + "')", uid, pwd);
    }

    @Override
    protected void createXmlResource(final String resourceUri, final String content, final String uid, final String pwd) throws ApiException {
        try {
            final String uri = getServerUri() + resourceUri;
            final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(uri), uid, pwd)
                    .header("Content-Type", "application/xml")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content.getBytes()))
                    .build();
            final HttpResponseResult result = AbstractHttpTest.executeForStatusAndBody(newHttpClient(), request);
            if (result.statusCode() != HTTP_CREATED) {
                throw new ApiException("Could not store XML resource to uri: " + resourceUri + ". " + result.body());
            }
        } catch (final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    @Override
    protected void createBinResource(final String resourceUri, final byte[] content, final String uid, final String pwd) throws ApiException {
        try {
            final String uri = getServerUri() + resourceUri;
            final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(uri), uid, pwd)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                    .build();
            final HttpResponseResult result = AbstractHttpTest.executeForStatusAndBody(newHttpClient(), request);
            if (result.statusCode() != HTTP_CREATED) {
                throw new ApiException("Could not store Binary resource to uri: " + resourceUri + ". " + result.body());
            }
        } catch (final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    private void executeQuery(final String xquery, final String uid, final String pwd) throws ApiException {
        try {
            final String queryUri = createQueryUri(xquery);

            final HttpRequest request = AbstractHttpTest.authenticatedRequest(URI.create(queryUri), uid, pwd)
                    .GET()
                    .build();
            final HttpResponseResult result = AbstractHttpTest.executeForStatusAndBody(newHttpClient(), request);
            final int status = result.statusCode();
            if (status != HTTP_OK) {
                throw new ApiException("HTTP " + status + " could not execute query uri: " + queryUri + ". " + result.body());
            }
        } catch (final IOException ioe) {
            throw new ApiException(ioe);
        }
    }

    private static HttpClient newHttpClient() {
        return AbstractHttpTest.newHttpClient();
    }

    private String createQueryUri(final String xquery) {
        return getServerUri() + baseUri + "/?_query=" + URLEncoder.encode(xquery, StandardCharsets.UTF_8);
    }
}
