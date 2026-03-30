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
package org.exist.xquery.modules.xslfo;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ApacheFopTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    public void simplePdf() throws EXistException, PermissionDeniedException, XPathException {
        final String fopConfig =
                """
                <fop version="1.0">
                    <strict-configuration>true</strict-configuration>
                    <strict-validation>false</strict-validation>
                    <base>./</base>
                    <renderers>
                        <renderer mime="application/pdf"></renderer>
                    </renderers>
                </fop>""";

        final String fo =
                """
                <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
                    <fo:layout-master-set>
                        <fo:simple-page-master master-name="page-left" page-height="297mm" page-width="210mm" margin-bottom="10mm" margin-top="10mm" margin-left="36mm" margin-right="18mm">
                            <fo:region-body margin-bottom="10mm" margin-top="16mm"/>
                            <fo:region-before region-name="head-left" extent="10mm"/>
                        </fo:simple-page-master>
                        <fo:simple-page-master master-name="page-right" page-height="297mm" page-width="210mm" margin-bottom="10mm" margin-top="10mm" margin-left="18mm" margin-right="36mm">
                            <fo:region-body margin-bottom="10mm" margin-top="16mm"/>
                            <fo:region-before region-name="head-right" extent="10mm"/>
                        </fo:simple-page-master>
                        <fo:page-sequence-master master-name="page-content">
                            <fo:repeatable-page-master-alternatives>
                                <fo:conditional-page-master-reference master-reference="page-right" odd-or-even="odd"/>
                                <fo:conditional-page-master-reference master-reference="page-left" odd-or-even="even"/>
                            </fo:repeatable-page-master-alternatives>
                        </fo:page-sequence-master>
                    </fo:layout-master-set>
                    <fo:page-sequence master-reference="page-content">
                         <fo:flow flow-name="xsl-region-body" hyphenate="true" language="en" xml:lang="en">
                                <fo:block id="A97060-t" line-height="16pt" font-size="11pt">
                                    <fo:block id="A97060-e0" page-break-after="right">
                                        <fo:block id="A97060-e100" text-align="justify" space-before=".5em" text-indent="1.5em" space-after=".5em">
                                            Hello World!
                                        </fo:block>
                                    </fo:block>
                                </fo:block>
                        </fo:flow>
                    </fo:page-sequence>
                </fo:root>""";

        final String xquery =
                "xquery version \"3.1\";\n" +
                "\n" +
                "import module namespace xslfo=\"http://exist-db.org/xquery/xslfo\";\n" +
                "\n" +
                "let $config := " + fopConfig + "\n" +
                "let $fo := " + fo + "\n" +
                "\n" +
                "let $pdf := xslfo:render($fo, \"application/pdf\", (), $config)\n" +
                "return $pdf";

        final BrokerPool pool = server.getBrokerPool();
        final XQuery xqueryService = pool.getXQueryService();

        try (final DBBroker broker = pool.getBroker()) {
            final Sequence result = xqueryService.execute(broker, xquery, null);
            assertNotNull(result);
            assertEquals(1, result.getItemCount());
            final Item pdf = result.itemAt(0);
            assertEquals(Type.BASE64_BINARY, pdf.getType());
        }
    }
}
