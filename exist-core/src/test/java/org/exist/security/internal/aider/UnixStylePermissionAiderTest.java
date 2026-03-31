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
package org.exist.security.internal.aider;

import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.util.SyntaxException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class UnixStylePermissionAiderTest {

    public class SecurityTestPair {

        public SecurityTestPair(final String permissionString, final int permission) {
            this.permissionString = permissionString;
            this.permission = permission;
        }

        public String permissionString;
        public int permission;
    }
    
    @Test
    public void setUid_roundtrip() throws PermissionDeniedException {
        Permission permission = new UnixStylePermissionAider(365);
        assertFalse(permission.isSetUid());
        permission.setSetUid(true);
        assertTrue(permission.isSetUid());
        assertEquals(2413, permission.getMode());
        
        permission = new UnixStylePermissionAider(2413);
        assertTrue(permission.isSetUid());
        permission.setSetUid(false);
        assertFalse(permission.isSetUid());
        assertEquals(365, permission.getMode());
    }
    
    @Test
    public void setGid_roundtrip() throws PermissionDeniedException {
        Permission permission = new UnixStylePermissionAider(365);
        assertFalse(permission.isSetGid());
        permission.setSetGid(true);
        assertTrue(permission.isSetGid());
        assertEquals(1389, permission.getMode());
        
        permission = new UnixStylePermissionAider(1389);
        assertTrue(permission.isSetGid());
        permission.setSetGid(false);
        assertFalse(permission.isSetGid());
        assertEquals(365, permission.getMode());
    }
    
    @Test
    public void setSticky_roundtrip() throws PermissionDeniedException {
        Permission permission = new UnixStylePermissionAider(365);
        assertFalse(permission.isSticky());
        permission.setSticky(true);
        assertTrue(permission.isSticky());
        assertEquals(877, permission.getMode());
        
        permission = new UnixStylePermissionAider(877);
        assertTrue(permission.isSticky());
        permission.setSticky(false);
        assertFalse(permission.isSticky());
        assertEquals(365, permission.getMode());
    }

    @Test
    public void fromString_toString() throws SyntaxException {

        final List<SecurityTestPair> securityTestPairs = new ArrayList<>();
        securityTestPairs.add(new SecurityTestPair("rwxrwxrwx", 511));
        securityTestPairs.add(new SecurityTestPair("rwxrwx---", 504));
        securityTestPairs.add(new SecurityTestPair("rwx------", 448));
        securityTestPairs.add(new SecurityTestPair("------rwx", 7));
        securityTestPairs.add(new SecurityTestPair("---rwxrwx", 63));
        securityTestPairs.add(new SecurityTestPair("r--r--r--", 292));
        securityTestPairs.add(new SecurityTestPair("rwxr--r--", 484));
        securityTestPairs.add(new SecurityTestPair("rwxrw-rw-", 502));
        securityTestPairs.add(new SecurityTestPair("rwxr-xr-x", 493));
        securityTestPairs.add(new SecurityTestPair("--s------", 2112));
        securityTestPairs.add(new SecurityTestPair("--S------", 2048));
        securityTestPairs.add(new SecurityTestPair("-----s---", 1032));
        securityTestPairs.add(new SecurityTestPair("-----S---", 1024));
        securityTestPairs.add(new SecurityTestPair("--------t", 513));
        securityTestPairs.add(new SecurityTestPair("--------T", 512));

        for(final SecurityTestPair sec : securityTestPairs) {
            final UnixStylePermissionAider perm = UnixStylePermissionAider.fromString(sec.permissionString);
            assertEquals(sec.permission, perm.getMode());
            assertEquals(sec.permissionString, perm.toString());
        }
    }

    @Test(expected=SyntaxException.class)
    public void fromStringInvalidSyntax_tooShort() throws SyntaxException{
       UnixStylePermissionAider.fromString("rwx");
    }

    @Test(expected=SyntaxException.class)
    public void fromStringInvalidSyntax_invalidChars() throws SyntaxException{
       UnixStylePermissionAider.fromString("rwurwurwu");
    }
}
