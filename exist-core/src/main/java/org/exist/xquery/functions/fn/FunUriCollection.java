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

package org.exist.xquery.functions.fn;

import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

public class FunUriCollection extends BasicFunction {

    private static final String FN_NAME = "uri-collection";
    private static final String FN_DESCRIPTION = "Returns a sequence of xs:anyURI values that represent the URIs in a URI collection.";
    private static final FunctionReturnSequenceType FN_RETURN = returnsOptMany(Type.STRING,
            "the default URI collection, if $arg is not specified or is an empty sequence, " +
                    "or the sequence of URIs that correspond to the supplied URI");
    private static final FunctionParameterSequenceType ARG = optParam("arg", Type.STRING, "The base-URI property from the static context, or an empty sequence");
    public static final FunctionSignature[] FS_URI_COLLECTION_SIGNATURES = functionSignatures(
            FN_NAME,
            FN_DESCRIPTION,
            FN_RETURN,
            arities(
                    arity(),
                    arity(ARG)
            )
        );

    private static final String KEY_CONTENT_TYPE = "content-type";
    private static final String VALUE_CONTENT_TYPE_DOCUMENT = "application/vnd.existdb.document";
    private static final String VALUE_CONTENT_TYPE_DOCUMENT_BINARY = "application/vnd.existdb.document+binary";
    private static final String VALUE_CONTENT_TYPE_DOCUMENT_XML = "application/vnd.existdb.document+xml";
    private static final String VALUE_CONTENT_TYPE_SUBCOLLECTION = "application/vnd.existdb.collection";
    private static final String[] VALUE_CONTENT_TYPES = {
            VALUE_CONTENT_TYPE_DOCUMENT,
            VALUE_CONTENT_TYPE_DOCUMENT_BINARY,
            VALUE_CONTENT_TYPE_DOCUMENT_XML,
            VALUE_CONTENT_TYPE_SUBCOLLECTION
    };

    private static final String KEY_STABLE = "stable";
    private static final String VALUE_STABLE_NO = "no";
    private static final String VALUE_STABLE_YES = "yes";
    private static final String[] VALUE_STABLES = {
            VALUE_STABLE_NO,
            VALUE_STABLE_YES
    };

    private static final String KEY_MATCH = "match";

    public FunUriCollection(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence result;
        if (args.length == 0 || args[0].isEmpty() || args[0].toString().isEmpty()) {
            if (XQueryContext.DEFAULT_URI_COLLECTION != null && XQueryContext.DEFAULT_URI_COLLECTION.length() > 0) {
                result = new StringValue(XQueryContext.DEFAULT_URI_COLLECTION);
            } else {
                throw new XPathException(this, ErrorCodes.FODC0002, "No URI is supplied and default resource collection is absent.");
            }
        } else {
            final List<String> resultUris = new LinkedList<>();

            final String uriWithQueryString = args[0].toString();
            final int queryStringIndex = uriWithQueryString.indexOf('?');
            final String uriWithoutQueryString = (queryStringIndex >= 0) ? uriWithQueryString.substring(0, queryStringIndex) : uriWithQueryString;
            String uriWithoutStableQueryString = uriWithQueryString.replaceAll(String.format("%s\\s*=\\s*\\byes|no\\b\\s*&+", KEY_STABLE), "");
            if (uriWithoutStableQueryString.endsWith("?")) {
                uriWithoutStableQueryString = uriWithoutStableQueryString.substring(0, uriWithoutStableQueryString.length() - 1);
            }

            final XmldbURI uri;
            try {
                uri = XmldbURI.xmldbUriFor(uriWithoutQueryString);
            } catch (URISyntaxException e) {
                throw new XPathException(this, ErrorCodes.FODC0004, String.format("\"%s\" is not a valid URI.", args[0].toString()));
            }

            final Map<String, String> queryStringMap = parseQueryString(uriWithQueryString);
            checkQueryStringMap(queryStringMap);

            if ((!queryStringMap.containsKey(KEY_STABLE) || queryStringMap.get(KEY_STABLE).equals(VALUE_STABLE_YES)) &&
                    context.getCachedUriCollectionResults().containsKey(uriWithoutStableQueryString)) {
                result = context.getCachedUriCollectionResults().get(uriWithoutStableQueryString);
            } else {
                final boolean binaryUrisIncluded = !queryStringMap.containsKey(KEY_CONTENT_TYPE) ||
                        (queryStringMap.get(KEY_CONTENT_TYPE).equals(VALUE_CONTENT_TYPE_DOCUMENT) ||
                         queryStringMap.get(KEY_CONTENT_TYPE).equals(VALUE_CONTENT_TYPE_DOCUMENT_BINARY));
                final boolean subcollectionUrisIncluded = !queryStringMap.containsKey(KEY_CONTENT_TYPE) ||
                        queryStringMap.get(KEY_CONTENT_TYPE).equals(VALUE_CONTENT_TYPE_SUBCOLLECTION);
                final boolean xmlUrisIncluded = !queryStringMap.containsKey(KEY_CONTENT_TYPE) ||
                        (queryStringMap.get(KEY_CONTENT_TYPE).equals(VALUE_CONTENT_TYPE_DOCUMENT) ||
                                queryStringMap.get(KEY_CONTENT_TYPE).equals(VALUE_CONTENT_TYPE_DOCUMENT_XML));

                try (final Collection collection = context.getBroker().openCollection(uri, Lock.LockMode.READ_LOCK)) {
                    if (binaryUrisIncluded || xmlUrisIncluded) {
                        final Iterator<DocumentImpl> documentIterator = collection.iterator(context.getBroker());
                        while (documentIterator.hasNext()) {
                            final DocumentImpl document = documentIterator.next();
                            if ((xmlUrisIncluded && !(document instanceof BinaryDocument)) ||
                                    (binaryUrisIncluded && document instanceof BinaryDocument)) {
                                resultUris.add(document.getURI().toString());
                            }
                        }
                    }

                    if (subcollectionUrisIncluded) {
                        final Iterator<XmldbURI> collectionsIterator = collection.collectionIterator(context.getBroker());
                        while (collectionsIterator.hasNext()) {
                            resultUris.add(uri.append(collectionsIterator.next()).toString());
                        }
                    }
                } catch (final LockException | PermissionDeniedException e) {
                    throw new XPathException(this, ErrorCodes.FODC0002, e);
                }

                if (queryStringMap.containsKey(KEY_MATCH) && queryStringMap.get(KEY_MATCH).length() > 0) {
                    final Pattern pattern = Pattern.compile(queryStringMap.get(KEY_MATCH));
                    final List<String> matchedResultUris = resultUris.stream().filter(resultUri -> pattern.matcher(resultUri).find()).collect(Collectors.toList());
                    if (matchedResultUris.isEmpty()) {
                        result = Sequence.EMPTY_SEQUENCE;
                    } else {
                        result = new ValueSequence();
                        for (String resultUri : matchedResultUris) {
                            result.add(new StringValue(resultUri));
                        }
                    }
                } else {
                    result = new ValueSequence();
                    for (String resultUri : resultUris) {
                        result.add(new StringValue(resultUri));
                    }
                }
                context.getCachedUriCollectionResults().put(uriWithoutStableQueryString, result);
            }
        }

        return result;
    }

    private static Map<String, String> parseQueryString(final String uri) {
        final Map<String, String> map = new HashMap<>();
        if (uri != null) {
            final int questionMarkIndex = uri.indexOf('?');
            if (questionMarkIndex >= 0 && questionMarkIndex + 1 < uri.length()) {
                String[] keyValuePairs = uri.substring(questionMarkIndex + 1).split("&");
                for (String keyValuePair : keyValuePairs) {
                    int equalIndex = keyValuePair.indexOf('=');
                    if (equalIndex >= 0) {
                        if (equalIndex + 1 < uri.length()) {
                            map.put(keyValuePair.substring(0, equalIndex).trim(), keyValuePair.substring(equalIndex + 1).trim());
                        } else {
                            map.put(keyValuePair.substring(0, equalIndex).trim(), "");
                        }
                    } else {
                        map.put(keyValuePair.trim(), "");
                    }
                }
            }
        }

        return map;
    }

    private void checkQueryStringMap(final Map<String, String> queryStringMap) throws XPathException {
        for (Map.Entry<String, String> queryStringEntry : queryStringMap.entrySet()) {
            final String key = queryStringEntry.getKey();
            final String value = queryStringEntry.getValue();
            if (key.equals(KEY_CONTENT_TYPE)) {
                if (Arrays.stream(VALUE_CONTENT_TYPES).noneMatch(contentTypeValue -> contentTypeValue.equals(value))) {
                    throw new XPathException(this, ErrorCodes.FODC0004, String.format("Invalid query-string value \"%s\".", queryStringEntry));
                }
            } else if (key.equals(KEY_STABLE)) {
                if (Arrays.stream(VALUE_STABLES).noneMatch(stableValue -> stableValue.equals(value))) {
                    throw new XPathException(this, ErrorCodes.FODC0004, String.format("Invalid query-string value \"%s\".", queryStringEntry));
                }
            } else if (!key.equals(KEY_MATCH)) {
                throw new XPathException(this, ErrorCodes.FODC0004, String.format("Unexpected query string \"%s\".", queryStringEntry));
            }
        }
    }
}
