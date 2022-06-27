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
package org.exist.xquery.functions.util;

import org.easymock.EasyMock;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class BaseConversionFunctionsTest {

    /**
     * Test of eval method, of class PermissionsFunctions.
     */
    @Test
    public void intToOctal() throws XPathException {
       final XQueryContext mckContext = EasyMock.createMock(XQueryContext.class);

       final BaseConversionFunctions baseConversionFunctions = new BaseConversionFunctions(mckContext, BaseConversionFunctions.FNS_INT_TO_OCTAL);
       Sequence args[] = {
           new IntegerValue(null, 511)
       };
       
       final Sequence result = baseConversionFunctions.eval(args, null);
       
       assertEquals(1, result.getItemCount());
       assertEquals("0777", result.itemAt(0).toString());
    }
    
    @Test
    public void octalToInt() throws XPathException {
       final XQueryContext mckContext = EasyMock.createMock(XQueryContext.class);

       final BaseConversionFunctions baseConversionFunctions = new BaseConversionFunctions(mckContext, BaseConversionFunctions.FNS_OCTAL_TO_INT);
       Sequence args[] = {
           new StringValue(null, "0777")
       };
       
       final Sequence result = baseConversionFunctions.eval(args, null);
       
       assertEquals(1, result.getItemCount());
       assertEquals(511, (int)result.itemAt(0).toJavaObject(int.class));
    }
}
