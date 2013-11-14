package org.exist.security.internal.aider;

import java.util.List;
import java.util.ArrayList;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.util.SyntaxException;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
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
        Permission permission = new UnixStylePermissionAider(0555);
        assertFalse(permission.isSetUid());
        permission.setSetUid(true);
        assertTrue(permission.isSetUid());
        assertEquals(04555, permission.getMode());
        
        permission = new UnixStylePermissionAider(04555);
        assertTrue(permission.isSetUid());
        permission.setSetUid(false);
        assertFalse(permission.isSetUid());
        assertEquals(0555, permission.getMode());
    }
    
    @Test
    public void setGid_roundtrip() throws PermissionDeniedException {
        Permission permission = new UnixStylePermissionAider(0555);
        assertFalse(permission.isSetGid());
        permission.setSetGid(true);
        assertTrue(permission.isSetGid());
        assertEquals(02555, permission.getMode());
        
        permission = new UnixStylePermissionAider(02555);
        assertTrue(permission.isSetGid());
        permission.setSetGid(false);
        assertFalse(permission.isSetGid());
        assertEquals(0555, permission.getMode());
    }
    
    @Test
    public void setSticky_roundtrip() throws PermissionDeniedException {
        Permission permission = new UnixStylePermissionAider(0555);
        assertFalse(permission.isSticky());
        permission.setSticky(true);
        assertTrue(permission.isSticky());
        assertEquals(01555, permission.getMode());
        
        permission = new UnixStylePermissionAider(01555);
        assertTrue(permission.isSticky());
        permission.setSticky(false);
        assertFalse(permission.isSticky());
        assertEquals(0555, permission.getMode());
    }

    @Test
    public void fromString_toString() throws SyntaxException {

        final List<SecurityTestPair> securityTestPairs = new ArrayList<SecurityTestPair>();
        securityTestPairs.add(new SecurityTestPair("rwxrwxrwx", 0777));
        securityTestPairs.add(new SecurityTestPair("rwxrwx---", 0770));
        securityTestPairs.add(new SecurityTestPair("rwx------", 0700));
        securityTestPairs.add(new SecurityTestPair("------rwx", 07));
        securityTestPairs.add(new SecurityTestPair("---rwxrwx", 077));
        securityTestPairs.add(new SecurityTestPair("r--r--r--", 0444));
        securityTestPairs.add(new SecurityTestPair("rwxr--r--", 0744));
        securityTestPairs.add(new SecurityTestPair("rwxrw-rw-", 0766));
        securityTestPairs.add(new SecurityTestPair("rwxr-xr-x", 0755));
        securityTestPairs.add(new SecurityTestPair("--s------", 04100));
        securityTestPairs.add(new SecurityTestPair("--S------", 04000));
        securityTestPairs.add(new SecurityTestPair("-----s---", 02010));
        securityTestPairs.add(new SecurityTestPair("-----S---", 02000));
        securityTestPairs.add(new SecurityTestPair("--------t", 01001));
        securityTestPairs.add(new SecurityTestPair("--------T", 01000));

        for(final SecurityTestPair sec : securityTestPairs) {
            final UnixStylePermissionAider perm = UnixStylePermissionAider.fromString(sec.permissionString);
            assertEquals(sec.permission, perm.getMode());
            assertEquals(sec.permissionString, perm.toString());
        }
    }

    @Test(expected=SyntaxException.class)
    public void fromStringInvalidSyntax_tooShort() throws SyntaxException{
       UnixStylePermissionAider.fromString("rwx");
    };

    @Test(expected=SyntaxException.class)
    public void fromStringInvalidSyntax_invalidChars() throws SyntaxException{
       UnixStylePermissionAider.fromString("rwurwurwu");
    };
}
