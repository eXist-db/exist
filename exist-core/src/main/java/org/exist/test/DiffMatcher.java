/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.test;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.w3c.dom.Node;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;
import org.xmlunit.util.Convert;

import javax.xml.transform.Source;

/**
 * Implementation of a Hamcrest Matcher
 * which will compare XML nodes.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DiffMatcher extends DiagnosingMatcher<Sequence> {
    private final Source expectedSource;
    private final boolean identical;

    private DiffMatcher(final Source expectedSource) {
        this(expectedSource, false);
    }

    private DiffMatcher(final Source expectedSource, final boolean identical) {
        this.expectedSource = expectedSource;
        this.identical = identical;
    }

    /**
     * Compares that the XML sources are similar.
     *
     * In this context "similar" is defined by {@link DiffBuilder#checkForSimilar()}.
     *
     * @param expectedSource the expected XML
     *
     * @return The Hamcrest Matcher
     */
    public static DiffMatcher hasSimilarXml(final Source expectedSource) {
        return new DiffMatcher(expectedSource);
    }

    /**
     * Compares that the XML sources are identical.
     *
     * In this context "similar" is defined by {@link DiffBuilder#checkForIdentical()} ()}.
     *
     * @param expectedSource the expected XML
     *
     * @return The Hamcrest Matcher
     */
    public static DiffMatcher hasIdenticalXml(final Source expectedSource) {
        return new DiffMatcher(expectedSource, true);
    }

    @Override
    public boolean matches(final Object item, final Description mismatch) {
        if (item == null) {
            mismatch.appendText("null");
            return false;
        }

        final Item actualItem;
        if (item instanceof NodeValue) {
            actualItem = (NodeValue) item;

        } else if (item instanceof Sequence actual) {

            if (actual.getItemCount() != 1) {
                mismatch.appendText("Sequence does not contain 1 item");
                return false;
            }

            actualItem = actual.itemAt(0);
            if (!(actualItem instanceof NodeValue)) {
                mismatch.appendText("Sequence does not contain a Node");
                return false;
            }
        } else {
            mismatch.appendText("is not a Node");
            return false;
        }

        final Source actualSource = Input.fromNode((org.w3c.dom.Node) actualItem).build();

        DiffBuilder diffBuilder = DiffBuilder.compare(expectedSource)
                .withTest(actualSource);
        if (identical) {
            diffBuilder = diffBuilder.checkForIdentical();
        } else {
            diffBuilder = diffBuilder.checkForSimilar();
        }

        final Diff diff = diffBuilder.build();
        if (diff.hasDifferences()) {
            mismatch.appendText("differences: " + diff);
            return false;
        }

        return true;
    }

    @Override
    public void describeTo(final Description description) {
        description
                .appendText("nodes match ")
                .appendValue(expectedSource);
    }

    /**
     * Creates an Document Source form an XML String.
     *
     * @param str a string representation of XML.
     *
     * @return a Document Source.
     */
    public static Source docSource(final String str) {
        return Input.fromString(str).build();
    }

    /**
     * Creates an Element Source form an XML String.
     *
     * @param str a string representation of XML.
     *
     * @return an Element Source.
     */
    public static Source elemSource(final String str) {
        final Node documentNode = Convert.toNode(docSource(str));
        final Node firstElement = documentNode.getFirstChild();
        return Input.fromNode(firstElement).build();
    }
}
