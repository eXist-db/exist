package org.exist.security.internal.aider;

import java.util.List;
import java.util.ArrayList;
import org.exist.util.SyntaxException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class UnixStylePermissionTest {

    public class SecurityTestPair {

        public SecurityTestPair(String permissionString, int permission) {
            this.permissionString = permissionString;
            this.permission = permission;
        }

        public String permissionString;
        public int permission;
    }

    @Test
    public void fromString() throws SyntaxException {

        List<SecurityTestPair> securityTestPairs = new ArrayList<SecurityTestPair>();
        securityTestPairs.add(new SecurityTestPair("rwxrwxrwx", 511));
        securityTestPairs.add(new SecurityTestPair("rwxrwx---", 504));
        securityTestPairs.add(new SecurityTestPair("rwx------", 448));
        securityTestPairs.add(new SecurityTestPair("------rwx", 7));
        securityTestPairs.add(new SecurityTestPair("---rwxrwx", 63));
        securityTestPairs.add(new SecurityTestPair("r--r--r--", 292));
        securityTestPairs.add(new SecurityTestPair("rwxr--r--", 484));
        securityTestPairs.add(new SecurityTestPair("rwxr--r--", 484));
        securityTestPairs.add(new SecurityTestPair("--s------", 2048));
        securityTestPairs.add(new SecurityTestPair("-----s---", 1024));
        securityTestPairs.add(new SecurityTestPair("--------t", 512));

        for(SecurityTestPair sec : securityTestPairs) {
            UnixStylePermissionAider perm = UnixStylePermissionAider.fromString(sec.permissionString);
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
