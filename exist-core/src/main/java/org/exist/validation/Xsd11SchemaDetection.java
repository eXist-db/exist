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
package org.exist.validation;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.resolver.ResolverFactory;
import org.xml.sax.InputSource;

/**
 * Stateless, context-free detection helpers for telling whether a schema needs XSD 1.1 to load --
 * shared between {@link org.exist.xquery.functions.validation.Jaxp} (where this logic originated)
 * and store-time document validation ({@code org.exist.collections.MutableCollection}), both of
 * which must route validation through an XSD 1.1-capable {@code Validator} instead of the default
 * dynamic-discovery SAX pipeline, since the bundled Xerces fork's XSD 1.1 support is only wired
 * into the JAXP {@code SchemaFactory}/{@code Validator} API, never into that pipeline.
 *
 * @see <a href="https://github.com/eXist-db/exist/issues/5541">#5541</a>
 */
public final class Xsd11SchemaDetection {

    private static final Logger LOG = LogManager.getLogger(Xsd11SchemaDetection.class);

    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    private static final String XSD_VERSIONING_NS = "http://www.w3.org/2007/XMLSchema-versioning";

    /**
     * Bound on the size of {@link #CACHE} (see there for what it caches).
     */
    private static final int CACHE_MAX_ENTRIES = 256;

    /**
     * Cache key for {@link #CACHE}: the requesting Subject's name plus the resolved schema URI.
     * Including the Subject prevents a Subject without read permission on the schema resource
     * from observing a boolean populated by a different (permitted) Subject's earlier,
     * permission-checked fetch -- a cache hit skips {@link #isXsd11Schema(String, String, String)}'s
     * {@code openStream()} entirely, so without this the cache itself would bypass whatever
     * permission check that open would otherwise perform.
     */
    private record CacheKey(String subjectName, String resolvedSchemaUri) {
    }

    /**
     * Bounded (see {@link #CACHE_MAX_ENTRIES}), LRU-evicted cache of "does the schema at this
     * resolved URI declare vc:minVersion 1.1?", so that validating many documents against the same
     * schema doesn't re-fetch and re-peek it every time. Cleared by {@link #clearCache()}.
     */
    private static final Cache<CacheKey, Boolean> CACHE = Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_ENTRIES)
            .build();

    private Xsd11SchemaDetection() {
    }

    /**
     * @return true if {@code message} is the "no global declaration for the root element"
     * signature ({@code cvc-elt.1.a}) produced when this Xerces fork's dynamic-discovery pipeline
     * meets an XSD 1.1-only schema.
     */
    public static boolean isMissingElementDeclaration(@Nullable final String message) {
        return message != null && message.contains("cvc-elt.1.a:");
    }

    /**
     * Cheaply checks whether the instance document references a schema via {@code
     * xsi:schemaLocation}/{@code xsi:noNamespaceSchemaLocation} that itself declares {@code
     * vc:minVersion} containing "1.1".
     *
     * @param subjectName the requesting Subject's name, used to scope {@link #CACHE} (see there
     *                     for why).
     * @param peekInstance a fresh, not-yet-consumed InputSource for the instance document.
     */
    public static boolean detectXsd11ViaSchemaLocation(final String subjectName, final InputSource peekInstance) {
        return detectXsd11ViaSchemaLocation(subjectName, peekRootElement(peekInstance), peekInstance.getSystemId());
    }

    /**
     * Same as {@link #detectXsd11ViaSchemaLocation(String, InputSource)}, but for callers that
     * already peeked the root element themselves (e.g. while also checking the root namespace in
     * the same StAX pass, see {@code org.exist.collections.MutableCollection}) -- avoids a second,
     * redundant peek of the same document.
     *
     * @param rootElement the instance's root element, as already peeked by the caller via
     *                     {@link #peekRootElement(InputSource)}, or {@code null} if that peek
     *                     failed (treated as "no hint").
     * @param baseUri the instance's base URI (see {@link InputSource#getSystemId()}).
     */
    public static boolean detectXsd11ViaSchemaLocation(final String subjectName, @Nullable final RootElementInfo rootElement, @Nullable final String baseUri) {
        if (rootElement == null || baseUri == null) {
            return false;
        }
        final Map<String, String> rootAttrs = rootElement.attributes();

        final List<String> candidateLocations = new ArrayList<>();
        final String noNsLocation = rootAttrs.get(clark(XSI_NS, "noNamespaceSchemaLocation"));
        if (noNsLocation != null) {
            candidateLocations.add(noNsLocation);
        }
        final String schemaLocation = rootAttrs.get(clark(XSI_NS, "schemaLocation"));
        if (schemaLocation != null) {
            // xsi:schemaLocation is a list of "namespace location" pairs; we only need the locations.
            final String[] tokens = schemaLocation.trim().split("\\s+");
            for (int i = 1; i < tokens.length; i += 2) {
                candidateLocations.add(tokens[i]);
            }
        }

        for (final String location : candidateLocations) {
            if (isXsd11Schema(subjectName, baseUri, location)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Resolves {@code location} relative to {@code baseUri} (the same xmldb:// normalization the
     * catalog mechanism uses, so this works for documents stored in the database), opens it, and
     * checks whether its root element declares {@code vc:minVersion} containing "1.1". Returns
     * {@code false} for any resolution/read failure -- this is a best-effort peek, not a
     * substitute for the real catalog-aware resolution the actual validation pass performs.
     *
     * <p>{@code location} is the literal, attacker/document-author-controlled value of the
     * instance's own {@code xsi:schemaLocation}/{@code noNamespaceSchemaLocation} hint -- if it's
     * an absolute URI (e.g. {@code file:///etc/passwd}, {@code http://internal-host/...}, or even
     * an absolute {@code xmldb://some-other-host:1234/db/...} naming a remote eXist instance),
     * {@link URI#resolve(String)} returns it verbatim, ignoring {@code baseUri} entirely. Opening
     * that unconditionally would let any caller make this (unprivileged, security-context-free)
     * peek fetch arbitrary local files or issue arbitrary outbound requests using the server
     * process's own OS-level/network access, regardless of the calling Subject's DB permissions --
     * this is NOT the same trust boundary as the real validation pass, which only fetches whatever
     * a configured catalog/resolver permits. So only resolutions that land in the exact same
     * scheme+authority (host/port) as {@code baseUri} are attempted -- i.e. genuinely relative
     * locations within the instance's own origin, never a different scheme or host. Anything else
     * falls through to the unchanged, already-accepted-risk default pipeline below, exactly as if
     * this peek didn't exist.</p>
     *
     * <p>{@code subjectName} scopes {@link #CACHE}: a cache hit skips this method's
     * permission-checked {@code openStream()} entirely, so without scoping by Subject, a Subject
     * without read permission on the schema resource could observe a boolean populated by a
     * different (permitted) Subject's earlier fetch -- a cross-Subject information leak.</p>
     */
    public static boolean isXsd11Schema(final String subjectName, final String baseUri, final String location) {
        try {
            final URI baseUriNormalized = new URI(ResolverFactory.fixupExistCatalogUri(baseUri));
            final URI resolvedUri = baseUriNormalized.resolve(location);
            if (!Objects.equals(baseUriNormalized.getScheme(), resolvedUri.getScheme())
                    || !Objects.equals(baseUriNormalized.getAuthority(), resolvedUri.getAuthority())) {
                LOG.debug("Refusing to peek candidate schema '{}': resolved to a different origin ('{}') than " +
                        "the instance's own base URI ('{}') -- leaving this to the default pipeline/catalog instead.",
                        location, resolvedUri, baseUriNormalized);
                return false;
            }

            final CacheKey cacheKey = new CacheKey(subjectName, resolvedUri.toString());
            final Boolean cached = CACHE.getIfPresent(cacheKey);
            if (cached != null) {
                return cached;
            }

            try (final InputStream is = resolvedUri.toURL().openStream()) {
                final InputSource schemaSource = new InputSource(is);
                schemaSource.setSystemId(resolvedUri.toString());
                final RootElementInfo schemaRootElement = peekRootElement(schemaSource);
                if (schemaRootElement == null) {
                    // Couldn't even parse the candidate as XML -- not a stable fact about a real
                    // schema, so don't cache it; let the next call retry.
                    return false;
                }
                final String minVersion = schemaRootElement.attributes().get(clark(XSD_VERSIONING_NS, "minVersion"));
                final boolean result = minVersion != null && minVersion.contains("1.1");
                CACHE.put(cacheKey, result);
                return result;
            }
        } catch (final URISyntaxException | IOException ex) {
            // Not cached: this may be a transient failure (lock contention, a brief network blip
            // for an xmldb:// catalog served over XML-RPC, etc); a permanently-cached false would
            // wrongly keep a legitimate schema on the slower retry-after-failure path forever.
            LOG.debug("Could not peek candidate schema '{}' relative to '{}': {}", location, baseUri, ex.getMessage());
            return false;
        }
    }

    /**
     * Discards all cached {@link #isXsd11Schema(String, String, String)} results.
     */
    public static void clearCache() {
        CACHE.invalidateAll();
    }

    /**
     * The root element's namespace URI (or {@code null} for no namespace) plus its attributes,
     * keyed by Clark-notation {@code {namespace}localName} (see {@link #clark(String, String)}) --
     * as returned by {@link #peekRootElement(InputSource)}.
     */
    public record RootElementInfo(@Nullable String namespaceUri, Map<String, String> attributes) {
    }

    /**
     * Reads only as far as the root element's start tag and returns its namespace URI and
     * attributes in one StAX pass -- cheaper than a full (validating) parse since StAX stops
     * pulling events the moment the caller stops asking for them, and cheaper than two separate
     * peeks for callers (such as {@code org.exist.collections.MutableCollection}) that need both
     * pieces of information about the same root element. DTD processing and external entities are
     * disabled; this reads untrusted instance documents as well as schema documents.
     *
     * @return the root element's info, or {@code null} if the source couldn't be read/parsed.
     */
    @Nullable
    public static RootElementInfo peekRootElement(final InputSource source) {
        try {
            final XMLInputFactory factory = hardenedXmlInputFactory();

            final Reader characterStream = source.getCharacterStream();
            final XMLStreamReader reader = characterStream != null
                    ? factory.createXMLStreamReader(characterStream)
                    : factory.createXMLStreamReader(source.getByteStream());
            try {
                while (reader.hasNext()) {
                    if (reader.next() == XMLStreamConstants.START_ELEMENT) {
                        final Map<String, String> attrs = new HashMap<>();
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attrs.put(clark(reader.getAttributeNamespace(i), reader.getAttributeLocalName(i)), reader.getAttributeValue(i));
                        }
                        return new RootElementInfo(reader.getNamespaceURI(), attrs);
                    }
                }
                return null;
            } finally {
                reader.close();
            }
        } catch (final XMLStreamException | NullPointerException ex) {
            LOG.debug("Could not peek root element: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * A freshly configured StAX {@link XMLInputFactory} with DTD processing and external entities
     * disabled -- shared hardening setup for {@link #peekRootElement(InputSource)} and
     * {@code org.exist.collections.MutableCollection}'s equivalent root-element peek, both of which
     * read untrusted instance/schema documents.
     */
    public static XMLInputFactory hardenedXmlInputFactory() {
        final XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }

    private static String clark(@Nullable final String namespaceUri, final String localName) {
        return (namespaceUri == null ? "" : "{" + namespaceUri + "}") + localName;
    }
}
