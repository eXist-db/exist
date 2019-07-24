/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.securitymanager;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class PermissionsFunctionModeConversionTest {

    /**
     * Test of eval method, of class PermissionsFunctions.
     */
    @Test
    public void modeToOctal() throws XPathException {
       final XQueryContext mckContext = EasyMock.createMock(XQueryContext.class);

       final PermissionsFunction permissionsFunctions = new PermissionsFunction(mckContext, PermissionsFunction.FNS_MODE_TO_OCTAL);
       Sequence args[] = {
           new StringValue("rwxr-x---")
       };
       
       final Sequence result = permissionsFunctions.eval(args, null);
       
       assertEquals(1, result.getItemCount());
       assertEquals("0750", result.itemAt(0).toString());
    }
    
    @Test(expected=XPathException.class)
    public void modeToOctal_invalidMode() throws XPathException {
       final XQueryContext mckContext = EasyMock.createMock(XQueryContext.class);

       final PermissionsFunction permissionsFunctions = new PermissionsFunction(mckContext, PermissionsFunction.FNS_MODE_TO_OCTAL);
       Sequence args[] = {
           new StringValue("invalid")
       };
       
       permissionsFunctions.eval(args, null);
    }
    
    @Test
    public void octalToMode() throws XPathException {
       final XQueryContext mckContext = EasyMock.createMock(XQueryContext.class);

       final PermissionsFunction permissionsFunctions = new PermissionsFunction(mckContext, PermissionsFunction.FNS_OCTAL_TO_MODE);
       Sequence args[] = {
           new StringValue("0750")
       };
       
       final Sequence result = permissionsFunctions.eval(args, null);
       
       assertEquals(1, result.getItemCount());
       assertEquals("rwxr-x---", result.itemAt(0).toString());
    }
}
