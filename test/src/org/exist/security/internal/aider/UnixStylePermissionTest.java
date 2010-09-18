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
        securityTestPairs.add(new SecurityTestPair("rwurwurwu", 511));
        securityTestPairs.add(new SecurityTestPair("rwurwu---", 504));
        securityTestPairs.add(new SecurityTestPair("rwu------", 448));
        securityTestPairs.add(new SecurityTestPair("------rwu", 7));
        securityTestPairs.add(new SecurityTestPair("---rwurwu", 63));
        securityTestPairs.add(new SecurityTestPair("r--r--r--", 292));
        securityTestPairs.add(new SecurityTestPair("rwur--r--", 484));


        for(SecurityTestPair sec : securityTestPairs) {
            UnixStylePermission perm = UnixStylePermission.fromString(sec.permissionString);
            assertEquals(sec.permission, perm.getPermissions());
            assertEquals(sec.permissionString, perm.toString());
        }
    }

    @Test(expected=SyntaxException.class)
    public void fromStringInvalidSyntax_tooShort() throws SyntaxException{
       UnixStylePermission.fromString("rwu");
    };

    @Test(expected=SyntaxException.class)
    public void fromStringInvalidSyntax_invalidChars() throws SyntaxException{
       UnixStylePermission.fromString("rwxrwxrwx");
    };
}
