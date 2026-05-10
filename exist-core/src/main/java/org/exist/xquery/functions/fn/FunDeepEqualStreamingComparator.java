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

import com.ibm.icu.text.Collator;
import org.exist.Namespaces;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.numbering.NodeId;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Streaming fast-path for fn:deep-equal on persistent-DOM trees (GH-4050).
 *
 * <p>The recursive {@link FunDeepEqual} implementation walks both subtrees
 * via {@code getFirstChild} / {@code getNextSibling} and inspects each node
 * via {@code getNamespaceURI} / {@code getLocalName} / {@code getAttributes}.
 * On persistent NodeProxy values every accessor materialises a fresh
 * {@code ElementImpl} from the BTree, so the cost scales with tree size
 * times accessor count per node. On the GH-4050 reproducer (Macbeth.xml,
 * 3,550 elements) that is ~5,500 ms, ~24x slower than xmldiff:compare's
 * 228 ms.
 *
 * <p>This class walks the same trees as event streams via
 * {@link IEmbeddedXMLStreamReader}, which iterates the BTree binary node
 * stream directly. Per-element work is bounded by the events the reader
 * already produces (qname, attribute list, character data) plus the
 * comparator's per-event compare. There is no per-node round-trip to
 * the storage layer beyond the linear iterator advance.
 *
 * <p>Semantics match {@code FunDeepEqual.compareElements} /
 * {@code FunDeepEqual.compareContents}:
 * <ul>
 *     <li>Element name comparison uses expanded QName (namespace URI +
 *     local name), code-point ordering for names.</li>
 *     <li>Attributes are order-insensitive: both sides are gathered,
 *     {@code xmlns:*} declarations are filtered, and the remainders are
 *     sorted by (namespace URI, local name) and compared positionally.
 *     Values are compared with the supplied collator.</li>
 *     <li>Comments and processing instructions are skipped per the
 *     fn:deep-equal spec.</li>
 *     <li>Character / CDATA events are compared in document order with
 *     the supplied collator. Adjacent text-event coalescing is not
 *     attempted; eXist's persistent DOM stores text runs as a single
 *     node, so adjacent {@code CHARACTERS} events do not occur in
 *     practice on stored documents.</li>
 * </ul>
 *
 * <p>Out of scope: schema-aware typed-value comparison (untyped only),
 * memtree (in-memory) nodes, atomic / map / array / attribute / text-as-top-level
 * items. The caller must dispatch only persistent {@code DOCUMENT} or
 * {@code ELEMENT} NodeHandle pairs.
 */
final class FunDeepEqualStreamingComparator {

    private static final int EOF = -1;
    private static final AttrSnapshot[] EMPTY_ATTRS = new AttrSnapshot[0];
    private static final Comparator<AttrSnapshot> ATTR_ORDER = (x, y) -> {
        final int nsCmp = compareNullable(x.ns, y.ns);
        if (nsCmp != 0) {
            return nsCmp;
        }
        return compareNullable(x.local, y.local);
    };

    private FunDeepEqualStreamingComparator() {}

    /**
     * Compare two persistent-DOM nodes (DOCUMENT or ELEMENT) via streaming.
     *
     * @param broker active database broker.
     * @param a first node.
     * @param b second node.
     * @param subtree {@code true} when both inputs are ELEMENT-rooted;
     * {@code false} when document-level (the dispatcher resolves each
     * document's first stored child via {@link #documentRoot}).
     * @param collator collation used to compare attribute values and text;
     * {@code null} = code-point.
     * @return {@link Constants#EQUAL} / {@link Constants#INFERIOR} /
     * {@link Constants#SUPERIOR} (sign indicates ordering for sort use).
     * @throws XMLStreamException on stream-level failure
     * @throws IOException on storage-level failure
     */
    static int compare(final DBBroker broker, final NodeProxy a, final NodeProxy b,
            final boolean subtree, @Nullable final Collator collator)
            throws XMLStreamException, IOException {
        final NodeHandle aHandle = subtree ? a : documentRoot(a);
        final NodeHandle bHandle = subtree ? b : documentRoot(b);
        if (aHandle == null || bHandle == null) {
            // Empty document or non-element first child; signal to caller
            // that the legacy path should handle this edge case.
            throw new XMLStreamException("streaming fast path: document has no element root");
        }
        // Both DOCUMENT and ELEMENT cases reduce to a subtree walk after
        // documentRoot() resolves the document's first stored child.
        final IEmbeddedXMLStreamReader ra = broker.newXMLStreamReader(aHandle, false);
        try {
            final IEmbeddedXMLStreamReader rb = broker.newXMLStreamReader(bHandle, false);
            try {
                return walk(ra, rb, /*subtree=*/true, collator);
            } finally {
                rb.close();
            }
        } finally {
            ra.close();
        }
    }

    /**
     * For document-level comparison, the StAX reader is initialised on the
     * document's first stored child (the root element on most XML
     * documents; first comment/PI in pathological cases). We obtain the
     * concrete StoredNode via {@code DocumentImpl.getFirstChild()} so the
     * reader's seek operates on a known-valid address.
     *
     * <p>This restricts the streaming fast path to single-root-element
     * documents — the common case for GH-4050. Documents with leading
     * comments/PIs trigger the legacy fallback when the first stored child
     * is not the root element.
     */
    @Nullable
    private static NodeHandle documentRoot(final NodeProxy n) {
        final DocumentImpl doc = n.getOwnerDocument();
        if (doc.getChildCount() == 0) {
            return null;
        }
        final org.w3c.dom.Node firstChild = doc.getFirstChild();
        if (firstChild instanceof NodeHandle nh) {
            return nh;
        }
        return null;
    }

    private static int walk(final IEmbeddedXMLStreamReader ra,
            final IEmbeddedXMLStreamReader rb, final boolean subtree,
            @Nullable final Collator collator) throws XMLStreamException {
        int depth = 0;
        boolean rootSeen = false;
        while (true) {
            final int evA = nextRelevantEvent(ra);
            final int evB = nextRelevantEvent(rb);
            if (evA == EOF && evB == EOF) {
                return Constants.EQUAL;
            }
            if (evA == EOF) {
                return Constants.INFERIOR;
            }
            if (evB == EOF) {
                return Constants.SUPERIOR;
            }
            if (evA != evB) {
                return evA < evB ? Constants.INFERIOR : Constants.SUPERIOR;
            }
            switch (evA) {
                case XMLStreamConstants.START_ELEMENT -> {
                    final int nameCmp = compareElementName(ra, rb);
                    if (nameCmp != Constants.EQUAL) {
                        return nameCmp;
                    }
                    final int attrCmp = compareAttributes(ra, rb, collator);
                    if (attrCmp != Constants.EQUAL) {
                        return attrCmp;
                    }
                    depth++;
                    rootSeen = true;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    depth--;
                    if (subtree && depth == 0 && rootSeen) {
                        return Constants.EQUAL;
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    final int textCmp = safeCompare(ra.getText(), rb.getText(), collator);
                    if (textCmp != Constants.EQUAL) {
                        return textCmp;
                    }
                }
                case XMLStreamConstants.SPACE -> {
                    final int textCmp = safeCompare(ra.getText(), rb.getText(), collator);
                    if (textCmp != Constants.EQUAL) {
                        return textCmp;
                    }
                }
                default -> {
                    // Stream produced an event we did not anticipate; fall back
                    // to caller's slow path by signalling INFERIOR. This is the
                    // safety valve for edge cases in the stored-DOM stream.
                    throw new XMLStreamException(
                            "Streaming comparator: unexpected event type " + evA);
                }
            }
        }
    }

    private static int nextRelevantEvent(final IEmbeddedXMLStreamReader r)
            throws XMLStreamException {
        while (r.hasNext()) {
            final int ev = r.next();
            if (ev == XMLStreamConstants.COMMENT
                    || ev == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                continue;
            }
            return ev;
        }
        return EOF;
    }

    private static int compareElementName(final IEmbeddedXMLStreamReader ra,
            final IEmbeddedXMLStreamReader rb) {
        final org.exist.dom.QName qa = ra.getQName();
        final org.exist.dom.QName qb = rb.getQName();
        final int nsCmp = safeCompare(qa.getNamespaceURI(), qb.getNamespaceURI(), null);
        if (nsCmp != Constants.EQUAL) {
            return nsCmp;
        }
        return safeCompare(qa.getLocalPart(), qb.getLocalPart(), null);
    }

    private static int compareAttributes(final IEmbeddedXMLStreamReader ra,
            final IEmbeddedXMLStreamReader rb, @Nullable final Collator collator) {
        final AttrSnapshot[] aa = collectSortedAttrs(ra);
        final AttrSnapshot[] bb = collectSortedAttrs(rb);
        if (aa.length != bb.length) {
            return aa.length < bb.length ? Constants.INFERIOR : Constants.SUPERIOR;
        }
        for (int i = 0; i < aa.length; i++) {
            int cmp = safeCompare(aa[i].ns, bb[i].ns, null);
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
            cmp = safeCompare(aa[i].local, bb[i].local, null);
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
            cmp = safeCompare(aa[i].value, bb[i].value, collator);
            if (cmp != Constants.EQUAL) {
                return cmp;
            }
        }
        return Constants.EQUAL;
    }

    private static AttrSnapshot[] collectSortedAttrs(final IEmbeddedXMLStreamReader r) {
        final int count = r.getAttributeCount();
        if (count == 0) {
            return EMPTY_ATTRS;
        }
        final AttrSnapshot[] tmp = new AttrSnapshot[count];
        int kept = 0;
        for (int i = 0; i < count; i++) {
            final String ns = r.getAttributeNamespace(i);
            // Filter out xmlns:* attributes; they are namespace declarations,
            // not data. FunDeepEqual.compareAttributes skips them via the
            // XMLNS_NS test.
            if (ns != null && Namespaces.XMLNS_NS.equals(ns)) {
                continue;
            }
            tmp[kept++] = new AttrSnapshot(
                    ns,
                    r.getAttributeLocalName(i),
                    r.getAttributeValue(i));
        }
        if (kept == count) {
            Arrays.sort(tmp, ATTR_ORDER);
            return tmp;
        }
        final AttrSnapshot[] out = new AttrSnapshot[kept];
        System.arraycopy(tmp, 0, out, 0, kept);
        Arrays.sort(out, ATTR_ORDER);
        return out;
    }

    private static int compareNullable(@Nullable final String a, @Nullable final String b) {
        // NOTE: intentional reference equality short-circuit (mirrors safeCompare).
        if (a == b) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    private static int safeCompare(@Nullable final String a, @Nullable final String b,
            @Nullable final Collator collator) {
        // NOTE: intentional reference equality short-circuit (matches FunDeepEqual.safeCompare).
        if (a == b) {
            return Constants.EQUAL;
        }
        if (a == null) {
            return Constants.INFERIOR;
        }
        if (b == null) {
            return Constants.SUPERIOR;
        }
        if (collator != null) {
            return collator.compare(a, b);
        }
        return a.compareTo(b);
    }

    private record AttrSnapshot(@Nullable String ns, String local, String value) {}
}
