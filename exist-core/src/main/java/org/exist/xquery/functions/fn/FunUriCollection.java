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
import org.exist.util.PatternFactory;
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
    private static final FunctionReturnSequenceType FN_RETURN = returnsOptMany(Type.ANY_URI,
            "the default URI collection, if $arg is not specified or is an empty sequence, " +
                    "or the sequence of URIs that correspond to the supplied URI");
    private static final FunctionParameterSequenceType ARG = optParam("arg", Type.STRING,
            "An xs:string identifying a URI Collection. " +
                    "The argument is interpreted as either an absolute xs:anyURI, or a relative xs:anyURI resolved " +
                    "against the base-URI property from the static context. In eXist-db this function consults the " +
                    "query hierarchy of the database. Query String parameters may be provided to " +
                    "control the URIs returned by this function. " +
                    "The parameter `match` may be used to provide a Regular Expression against which the result " +
                    "sequence of URIs are filtered. " +
                    "The parameter `content-type` may be used to determine the Internet Media Type (or generally " +
                    "whether XML, Binary, and/or (Sub) Collection) URIs that are returned in the result sequence; " +
                    "the special values: 'application/vnd.existdb.collection' includes (Sub) Collections, " +
                    "'application/vnd.existdb.document' includes any document, " +
                    "'application/vnd.existdb.document+xml' includes only XML documents, and " +
                    "'application/vnd.existdb.document+binary' includes only Binary documents. By default, " +
                    "`content-type=application/vnd.existdb.collection,application/vnd.existdb.document` " +
                    "(i.e. all Collections and Documents). " +
                    "The parameter `stable` may be used to determine if the function is deterministic. " +
                    "By default `stable=yes` to ensure that the same results are returned by each call within the same " +
                    "query."
    );
    public static final FunctionSignature[] FS_URI_COLLECTION_SIGNATURES = functionSignatures(
            FN_NAME,
            FN_DESCRIPTION,
            FN_RETURN,
            arities(
                    arity(),
                    arity(ARG)
            )
        );

    public FunUriCollection(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence result;
        if (args.length == 0 || args[0].isEmpty() || args[0].toString().isEmpty()) {
            result = new AnyURIValue(XmldbURI.ROOT_COLLECTION);
        } else {
            final List<String> resultUris = new ArrayList<>();

            final String uriWithQueryString = args[0].toString();
            final int queryStringIndex = uriWithQueryString.indexOf('?');
            final String uriWithoutQueryString = (queryStringIndex >= 0) ? uriWithQueryString.substring(0, queryStringIndex) : uriWithQueryString;
            final String uriWithoutStableQueryString = CollectionQueryParameters.stripStableParameter(uriWithQueryString);

            final XmldbURI uri;
            try {
                uri = XmldbURI.xmldbUriFor(uriWithoutQueryString);
            } catch (URISyntaxException e) {
                throw new XPathException(this, ErrorCodes.FODC0004, "\"%s\" is not a valid URI.".formatted(args[0].toString()));
            }

            final CollectionQueryParameters params = CollectionQueryParameters.parse(
                    uriWithQueryString, CollectionQueryParameters.URI_COLLECTION_KEYS, this);

            if (params.isStable() && context.getCachedUriCollectionResults().containsKey(uriWithoutStableQueryString)) {
                result = context.getCachedUriCollectionResults().get(uriWithoutStableQueryString);
            } else {
                final boolean binaryUrisIncluded = params.includesBinaryDocuments();
                final boolean subcollectionUrisIncluded = params.includesSubcollections();
                final boolean xmlUrisIncluded = params.includesXmlDocuments();

                try (final Collection collection = context.getBroker().openCollection(uri, Lock.LockMode.READ_LOCK)) {
                    if (collection != null) {
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
                    } else {
                        throw new XPathException(this, ErrorCodes.FODC0002, "Collection \"%s\" not found.".formatted(uri));
                    }
                } catch (final LockException | PermissionDeniedException e) {
                    throw new XPathException(this, ErrorCodes.FODC0002, e);
                }

                if (params.getMatch() != null && !params.getMatch().isEmpty()) {
                    final Pattern pattern = PatternFactory.getInstance().getPattern(params.getMatch());
                    final List<String> matchedResultUris = resultUris.stream().filter(resultUri -> pattern.matcher(resultUri).find()).collect(Collectors.toList());
                    if (matchedResultUris.isEmpty()) {
                        result = Sequence.EMPTY_SEQUENCE;
                    } else {
                        result = new ValueSequence();
                        for (String resultUri : matchedResultUris) {
                            result.add(new AnyURIValue(resultUri));
                        }
                    }
                } else {
                    result = new ValueSequence();
                    for (String resultUri : resultUris) {
                        result.add(new AnyURIValue(resultUri));
                    }
                }

                // only store the result if they were not previously stored - otherwise we loose stability!
                if (!context.getCachedUriCollectionResults().containsKey(uriWithoutStableQueryString)) {
                    context.getCachedUriCollectionResults().put(uriWithoutStableQueryString, result);
                }
            }
        }

        return result;
    }
}
