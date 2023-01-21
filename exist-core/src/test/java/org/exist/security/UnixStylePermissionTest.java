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
package org.exist.security;

import java.io.IOException;
import java.util.Random;

import static org.easymock.EasyMock.expect;

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import org.exist.security.internal.RealmImpl;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.SyntaxException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@SuppressWarnings("OctalInteger")
@RunWith(ParallelRunner.class)
public class UnixStylePermissionTest {

    @Test
    public void writeRead_roundtrip() throws IOException {

        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt();
        final int mode = 0700;
        final int ownerGroupId = new Random().nextInt();

        final VariableByteOutputStream mockOstream = EasyMock.createMock(VariableByteOutputStream.class);
        final VariableByteInput mockIstream = EasyMock.createMock(VariableByteInput.class);

        final TestableUnixStylePermission permission = new TestableUnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        
        final long permissionVector = permission.getVector_testable();
        
        //expectations
        mockOstream.writeLong(permissionVector);
        expect(mockIstream.readLong()).andReturn(permissionVector);

        replay(mockSecurityManager, mockOstream, mockIstream);

        permission.write(mockOstream);
        permission.read(mockIstream);

        verify(mockSecurityManager, mockOstream, mockIstream);

        assertEquals(permissionVector, permission.getVector_testable());
    }

    /**
     * Tests that if we are the owner of a resource, and that resource has read permission
     * granted to the owner, then we can read the resource
     */
    @Test
    public void validate_can_read_WhenOwnerWithRead() {

        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0700;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(ownerId);

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertTrue(isValid);
    }

    /**
     * Tests that if we are the owner of a resource, and that resource
     * does not have read permission granted to the owner,
     * then we cannot read the resource
     */
    @Test
    public void validate_cant_read_WhenOwnerWithoutRead() {
        
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0077;
        final int ownerGroupId = new Random(SecurityManagerImpl.MAX_GROUP_ID).nextInt();
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(ownerId);

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertFalse(isValid);
    }

    /**
     * Tests that if we are the owner of a resource, and that resource
     * does not have read permission granted to the owner,
     * then we cannot read the resource even if the owner
     * is in a group that has read permission on the resource
     */
    @Test
    public void validate_cant_read_WhenOwnerWithoutRead_and_OwnerInGroupWithRead() {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0077;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(ownerId);

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertFalse(isValid);
    }

    /**
     * Tests that if we are not the owner of a resource
     * but are in the group for the resource and that group
     * has read permission,
     * then we cannot read the resource even if other(s)
     * have read permission on the resource
     */
    @Test
    public void validate_cant_read_WhenNotOwner_and_InGroupWithoutRead_and_OtherCanRead() {

        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0007;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);
        final int userId = new Random(SecurityManagerImpl.MAX_GROUP_ID).nextInt();

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(userId);
        expect(mockUser.getGroupIds()).andReturn(new int[] { ownerGroupId });

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertFalse(isValid);
    }

     /**
     * Tests that if we are not the owner of a resource
     * but are in the group for the resource and that group
     * has read permission,
     * then we cannot read the resource even if other(s)
     * have read permission on the resource
     */
    @Test
    public void validate_can_read_WhenNotOwner_and_InGroupWithRead() {

        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0077;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);
        final int userId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(userId);
        expect(mockUser.getGroupIds()).andReturn(new int[] { ownerGroupId });

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertTrue(isValid);
    }

    @Test
    public void validate_can_read_WhenNotOwner_and_NotInGroup_and_OtherWithRead() {

        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random(SecurityManagerImpl.MAX_USER_ID).nextInt();
        final int mode = 0007;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);
        final int userId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(userId);
        expect(mockUser.getGroupIds()).andReturn(new int[] { new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID) });

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertTrue(isValid);
    }

    @Test
    public void validate_cant_write_WhenNotOwner_and_NotInGroup_and_OtherWithoutWrite() {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = RealmImpl.SYSTEM_ACCOUNT_ID;
        final int mode = 0001;
        final int ownerGroupId = RealmImpl.DBA_GROUP_ID;
        final boolean hasDbaRole = false;

        final Subject mockUser = EasyMock.createMock(Subject.class);
        final int userId = RealmImpl.GUEST_ACCOUNT_ID;
        final int[] userGroupIds = new int[] { RealmImpl.GUEST_GROUP_ID };

        expect(mockUser.hasDbaRole()).andReturn(hasDbaRole);
        expect(mockUser.getId()).andReturn(userId);
        expect(mockUser.getGroupIds()).andReturn(userGroupIds);

        replay(mockSecurityManager, mockUser);

        final Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        final boolean isValid = permission.validate(mockUser, Permission.WRITE);

        verify(mockSecurityManager, mockUser);

        assertFalse(isValid);
    }

    @Test
    public void permission_toString() {
        
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 07777);
        assertEquals("rwsrwsrwt", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 04777);
        assertEquals("rwsrwxrwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 04666);
        assertEquals("rwSrw-rw-", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 02777);
        assertEquals("rwxrwsrwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 02666);
        assertEquals("rw-rwSrw-", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 01777);
        assertEquals("rwxrwxrwt", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 07111);
        assertEquals("--s--s--t", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 07000);
        assertEquals("--S--S--T", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 04100);
        assertEquals("--s------", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 04000);
        assertEquals("--S------", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 02010);
        assertEquals("-----s---", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 02000);
        assertEquals("-----S---", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 01001);
        assertEquals("--------t", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 01000);
        assertEquals("--------T", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0777);
        assertEquals("rwxrwxrwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0770);
        assertEquals("rwxrwx---", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0700);
        assertEquals("rwx------", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0070);
        assertEquals("---rwx---", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0007);
        assertEquals("------rwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0744);
        assertEquals("rwxr--r--", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 0740);
        assertEquals("rwxr-----", permission.toString());
    }

    private void testSafeSetExecutable(final int inputMode, final int expectedMode) {
        final int permission = UnixStylePermission.safeSetExecutable(inputMode);
        final String message = Integer.toOctalString(expectedMode) + "<>" + Integer.toOctalString(permission);
        assertEquals(message, expectedMode, permission);
    }

    @Test
    public void permission_safeSetExecutable() {
        testSafeSetExecutable(0100, 0100);
        testSafeSetExecutable(0110, 0110);
        testSafeSetExecutable(0111, 0111);
        testSafeSetExecutable(0200, 0300);
        testSafeSetExecutable(0220, 0330);
        testSafeSetExecutable(0222, 0333);
        testSafeSetExecutable(0300, 0300);
        testSafeSetExecutable(0330, 0330);
        testSafeSetExecutable(0333, 0333);
        testSafeSetExecutable(0444, 0555);
        testSafeSetExecutable(0440, 0550);
        testSafeSetExecutable(0400, 0500);
        testSafeSetExecutable(0555, 0555);
        testSafeSetExecutable(0550, 0550);
        testSafeSetExecutable(0500, 0500);
        testSafeSetExecutable(0600, 0700);
        testSafeSetExecutable(0620, 0730);
        testSafeSetExecutable(0622, 0733);
        testSafeSetExecutable(0621, 0731);
        testSafeSetExecutable(0640, 0750);
        testSafeSetExecutable(0642, 0753);
        testSafeSetExecutable(0644, 0755);
        testSafeSetExecutable(0655, 0755);
        testSafeSetExecutable(0755, 0755);
        testSafeSetExecutable(0777, 0777);
        testSafeSetExecutable(0770, 0770);
        testSafeSetExecutable(0700, 0700);
        testSafeSetExecutable(0070, 0170);
        testSafeSetExecutable(0007, 0107);

        testSafeSetExecutable(07777,07777);
        testSafeSetExecutable(04777, 04777);
        testSafeSetExecutable(02777, 02777);
        testSafeSetExecutable(01777, 01777);

        testSafeSetExecutable(04666, 04777);
        testSafeSetExecutable(02666, 02777);

        testSafeSetExecutable(07111, 07111);
        testSafeSetExecutable(07000, 07100);
        testSafeSetExecutable(04000, 04100);

        testSafeSetExecutable(04100, 04100);
        testSafeSetExecutable(02010, 02110);
    }

    @Test
    public void permission_setFromModeString_existSymbolic() throws SyntaxException, PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("user=+read,+write,-execute");
        assertEquals(06, permission.getOwnerMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("user=+execute,group=+execute,other=+execute");
        assertEquals(0111, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0666);
        permission.setMode("user=+execute,group=+execute,other=+execute");
        assertEquals(0777, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0777);
        permission.setMode("user=-read,-write,-execute,group=-read,-write,-execute,other=-read,-write,-execute");
        assertEquals(0, permission.getMode());
    }
    
    @Test
    public void setUid_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        
        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        assertFalse(permission.isSetUid());
        permission.setSetUid(true);
        assertTrue(permission.isSetUid());
        assertEquals(04555, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 04555);
        assertTrue(permission.isSetUid());
        permission.setSetUid(false);
        assertFalse(permission.isSetUid());
        assertEquals(0555, permission.getMode());
    }
    
    @Test
    public void setGid_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        
        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        assertFalse(permission.isSetGid());
        permission.setSetGid(true);
        assertTrue(permission.isSetGid());
        assertEquals(02555, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 02555);
        assertTrue(permission.isSetGid());
        permission.setSetGid(false);
        assertFalse(permission.isSetGid());
        assertEquals(0555, permission.getMode());
    }
    
    @Test
    public void setSticky_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        
        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        assertFalse(permission.isSticky());
        permission.setSticky(true);
        assertTrue(permission.isSticky());
        assertEquals(01555, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 01555);
        assertTrue(permission.isSticky());
        permission.setSticky(false);
        assertFalse(permission.isSticky());
        assertEquals(0555, permission.getMode());
    }
    
    @Test
    public void permission_setFromModeString_unixSymbolic() throws SyntaxException, PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("u+rw-x");
        assertEquals(06, permission.getOwnerMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("+rw-x");
        assertEquals(0666, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("u+x,g+x,o+x");
        assertEquals(0111, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0666);
        permission.setMode("u+x,g+x,o+x");
        assertEquals(0777, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0777);
        permission.setMode("u-rwx,g-rwx,o-rwx");
        assertEquals(0, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("u+w");
        assertEquals(0755, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("u+w,g+w");
        assertEquals(0775, permission.getMode());
        
        //setUid
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("u+s");
        assertEquals(04555, permission.getMode());
        
        //setGid
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("g+s");
        assertEquals(02555, permission.getMode());
        
        //setUid + setGid
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("u+s,g+s");
        assertEquals(06555, permission.getMode());
        
        //setUid + setGid (simplified)
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("+s");
        assertEquals(06555, permission.getMode());
        
        //sticky
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("o+t");
        assertEquals(01555, permission.getMode());
        
        //sticky (simplified)
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0555);
        permission.setMode("+t");
        assertEquals(01555, permission.getMode());
    }

    @Test
    public void permission_setFromModeString_simpleSymbolic() throws SyntaxException, PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rw-------");
        assertEquals(06, permission.getOwnerMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwxrwx");
        assertEquals(0777, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0666);
        permission.setMode("--x--x--x");
        assertEquals(0111, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0777);
        permission.setMode("r--r--r--");
        assertEquals(0444, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0777);
        permission.setMode("---------");
        assertEquals(0, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwS------");
        assertEquals(04600, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwx------");
        assertEquals(0700, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rws------");
        assertEquals(04700, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwS---");
        assertEquals(02760, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwx---");
        assertEquals(0770, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrws---");
        assertEquals(02770, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwxrwt");
        assertEquals(01777, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwxrwT");
        assertEquals(01776, permission.getMode());
    }

    @Test
    public void permission_setMode_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, 1, 1, 0);

        for(int mode = 0; mode <= 07777; mode++) {
            permission.setMode(mode);
            assertEquals(mode, permission.getMode());
        }
    }

    static class TestableUnixStylePermission extends UnixStylePermission {

        public TestableUnixStylePermission(final SecurityManager sm, final int ownerId, final int ownerGroupId, final int mode) {
            super(sm, ownerId, ownerGroupId, mode);
        }

        public long getVector_testable() {
            return super.getVector();
        }

        public long encodeAsBitVector_testable(final int userId, final int groupId, final int mode) {
            return super.encodeAsBitVector(userId, groupId, mode);
        }
    }

    static class TestableUnixStylePermissionWithCurrentSubject extends UnixStylePermission {

        public TestableUnixStylePermissionWithCurrentSubject(final SecurityManager sm, final int ownerId, final int ownerGroupId, final int mode) {
            super(sm, ownerId, ownerGroupId, mode);
        }

        @Override
        public boolean isCurrentSubjectDBA() {
           return true;
        }

        @Override
        public boolean isCurrentSubjectInGroup() {
            return true;
        }

        @Override
        public boolean isCurrentSubjectOwner() {
            return true;
        }
    }
}