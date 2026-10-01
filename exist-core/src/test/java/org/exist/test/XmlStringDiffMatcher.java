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
package org.exist.test;

import org.hamcrest.Description;
import org.hamcrest.DiagnosingMatcher;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;

/**
 * Implementation of a Hamcrest Matcher
 * which will compare XML nodes constructed from Strings.
 */
public class XmlStringDiffMatcher extends DiagnosingMatcher<String> {
    private final Source expectedSource;
    private final boolean identical;

    private XmlStringDiffMatcher(final String expectedSource) {
        this(expectedSource, false);
    }

    private XmlStringDiffMatcher(final String expectedSource, final boolean identical) {
        this.expectedSource = docSource(expectedSource);
        this.identical = identical;
    }

    /**
     * Compares that the XML sources are similar.
     * <p>
     * In this context "similar" is defined by {@link DiffBuilder#checkForSimilar()}.
     *
     * @param expectedSource the expected XML
     * @return The Hamcrest Matcher
     */
    public static XmlStringDiffMatcher hasSimilarXml(final String expectedSource) {
        return new XmlStringDiffMatcher(expectedSource);
    }

    /**
     * Compares that the XML sources are identical.
     * <p>
     * In this context "similar" is defined by {@link DiffBuilder#checkForIdentical()} ()}.
     *
     * @param expectedSource the expected XML
     * @return The Hamcrest Matcher
     */
    public static XmlStringDiffMatcher hasIdenticalXml(final String expectedSource) {
        return new XmlStringDiffMatcher(expectedSource, true);
    }

    @Override
    public boolean matches(final Object item, final Description mismatch) {
        if (item == null) {
            mismatch.appendText("null");
            return false;
        }
        if (!(item instanceof String)) {
            mismatch.appendText("Not a String");
            return false;
        }

        final Source actualSource = docSource((String)item);
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
     * Creates a Document Source from an XML String.
     *
     * @param str a string representation of XML.
     * @return a Document Source.
     */
    public static Source docSource(final String str) {
        return Input.fromString(str).build();
    }
}

