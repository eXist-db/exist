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

import com.googlecode.junittoolbox.ParallelRunner;
import org.easymock.EasyMock;
import org.exist.security.internal.RealmImpl;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.SyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Random;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

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
        final int mode = 448;
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
        final int mode = 448;
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
        final int mode = 63;
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
        final int mode = 63;
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
        final int mode = 7;
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
        final int mode = 63;
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
        final int mode = 7;
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
        final int mode = 1;
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

        Permission permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 4095);
        assertEquals("rwsrwsrwt", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 2559);
        assertEquals("rwsrwxrwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 2486);
        assertEquals("rwSrw-rw-", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 1535);
        assertEquals("rwxrwsrwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 1462);
        assertEquals("rw-rwSrw-", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 1023);
        assertEquals("rwxrwxrwt", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 3657);
        assertEquals("--s--s--t", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 3584);
        assertEquals("--S--S--T", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 2112);
        assertEquals("--s------", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 2048);
        assertEquals("--S------", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 1032);
        assertEquals("-----s---", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 1024);
        assertEquals("-----S---", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 513);
        assertEquals("--------t", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 512);
        assertEquals("--------T", permission.toString());
        
        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 511);
        assertEquals("rwxrwxrwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 504);
        assertEquals("rwxrwx---", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 448);
        assertEquals("rwx------", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 56);
        assertEquals("---rwx---", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 7);
        assertEquals("------rwx", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 484);
        assertEquals("rwxr--r--", permission.toString());

        permission = new UnixStylePermission(mockSecurityManager, ownerId, ownerGroupId, 480);
        assertEquals("rwxr-----", permission.toString());
    }

    private void assertTestSafeExecutable(final int inputMode, final int expectedMode) {
        final int permission = UnixStylePermission.safeSetExecutable(inputMode);
        final String message = Integer.toOctalString(expectedMode) + "<>" + Integer.toOctalString(permission);
        assertEquals(message, expectedMode, permission);
    }

    @Test
    public void testSafeSetExecutable() {
        assertTestSafeExecutable(64, 64);
        assertTestSafeExecutable(72, 72);
        assertTestSafeExecutable(73, 73);
        assertTestSafeExecutable(128, 192);
        assertTestSafeExecutable(144, 216);
        assertTestSafeExecutable(146, 219);
        assertTestSafeExecutable(192, 192);
        assertTestSafeExecutable(216, 216);
        assertTestSafeExecutable(219, 219);
        assertTestSafeExecutable(292, 365);
        assertTestSafeExecutable(288, 360);
        assertTestSafeExecutable(256, 320);
        assertTestSafeExecutable(365, 365);
        assertTestSafeExecutable(360, 360);
        assertTestSafeExecutable(320, 320);
        assertTestSafeExecutable(384, 448);
        assertTestSafeExecutable(400, 472);
        assertTestSafeExecutable(402, 475);
        assertTestSafeExecutable(401, 473);
        assertTestSafeExecutable(416, 488);
        assertTestSafeExecutable(418, 491);
        assertTestSafeExecutable(420, 493);
        assertTestSafeExecutable(429, 493);
        assertTestSafeExecutable(493, 493);
        assertTestSafeExecutable(511, 511);
        assertTestSafeExecutable(504, 504);
        assertTestSafeExecutable(448, 448);
        assertTestSafeExecutable(56, 120);
        assertTestSafeExecutable(7, 71);

        assertTestSafeExecutable(4095,4095);
        assertTestSafeExecutable(2559, 2559);
        assertTestSafeExecutable(1535, 1535);
        assertTestSafeExecutable(1023, 1023);

        assertTestSafeExecutable(2486, 2559);
        assertTestSafeExecutable(1462, 1535);

        assertTestSafeExecutable(3657, 3657);
        assertTestSafeExecutable(3584, 3648);
        assertTestSafeExecutable(2048, 2112);

        assertTestSafeExecutable(2112, 2112);
        assertTestSafeExecutable(1032, 1096);
    }

    @Test
    public void permission_setFromModeString_existSymbolic() throws SyntaxException, PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("user=+read,+write,-execute");
        assertEquals(6, permission.getOwnerMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("user=+execute,group=+execute,other=+execute");
        assertEquals(73, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 438);
        permission.setMode("user=+execute,group=+execute,other=+execute");
        assertEquals(511, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 511);
        permission.setMode("user=-read,-write,-execute,group=-read,-write,-execute,other=-read,-write,-execute");
        assertEquals(0, permission.getMode());
    }
    
    @Test
    public void setUid_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        
        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        assertFalse(permission.isSetUid());
        permission.setSetUid(true);
        assertTrue(permission.isSetUid());
        assertEquals(2413, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 2413);
        assertTrue(permission.isSetUid());
        permission.setSetUid(false);
        assertFalse(permission.isSetUid());
        assertEquals(365, permission.getMode());
    }
    
    @Test
    public void setGid_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        
        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        assertFalse(permission.isSetGid());
        permission.setSetGid(true);
        assertTrue(permission.isSetGid());
        assertEquals(1389, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 1389);
        assertTrue(permission.isSetGid());
        permission.setSetGid(false);
        assertFalse(permission.isSetGid());
        assertEquals(365, permission.getMode());
    }
    
    @Test
    public void setSticky_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);
        
        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        assertFalse(permission.isSticky());
        permission.setSticky(true);
        assertTrue(permission.isSticky());
        assertEquals(877, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 877);
        assertTrue(permission.isSticky());
        permission.setSticky(false);
        assertFalse(permission.isSticky());
        assertEquals(365, permission.getMode());
    }
    
    @Test
    public void permission_setFromModeString_unixSymbolic() throws SyntaxException, PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("u+rw-x");
        assertEquals(6, permission.getOwnerMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("+rw-x");
        assertEquals(438, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("u+x,g+x,o+x");
        assertEquals(73, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 438);
        permission.setMode("u+x,g+x,o+x");
        assertEquals(511, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 511);
        permission.setMode("u-rwx,g-rwx,o-rwx");
        assertEquals(0, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("u+w");
        assertEquals(493, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("u+w,g+w");
        assertEquals(509, permission.getMode());
        
        //setUid
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("u+s");
        assertEquals(2413, permission.getMode());
        
        //setGid
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("g+s");
        assertEquals(1389, permission.getMode());
        
        //setUid + setGid
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("u+s,g+s");
        assertEquals(3437, permission.getMode());
        
        //setUid + setGid (simplified)
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("+s");
        assertEquals(3437, permission.getMode());
        
        //sticky
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("o+t");
        assertEquals(877, permission.getMode());
        
        //sticky (simplified)
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 365);
        permission.setMode("+t");
        assertEquals(877, permission.getMode());
    }

    @Test
    public void permission_setFromModeString_simpleSymbolic() throws SyntaxException, PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rw-------");
        assertEquals(6, permission.getOwnerMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwxrwx");
        assertEquals(511, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 438);
        permission.setMode("--x--x--x");
        assertEquals(73, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 511);
        permission.setMode("r--r--r--");
        assertEquals(292, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 511);
        permission.setMode("---------");
        assertEquals(0, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwS------");
        assertEquals(2432, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwx------");
        assertEquals(448, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rws------");
        assertEquals(2496, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwS---");
        assertEquals(1520, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwx---");
        assertEquals(504, permission.getMode());
        
        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrws---");
        assertEquals(1528, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwxrwt");
        assertEquals(1023, permission.getMode());

        permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, ownerId, ownerGroupId, 0);
        permission.setMode("rwxrwxrwT");
        assertEquals(1022, permission.getMode());
    }

    @Test
    public void permission_setMode_roundtrip() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final Permission permission = new TestableUnixStylePermissionWithCurrentSubject(mockSecurityManager, 1, 1, 0);

        for(int mode = 0; mode <= 4095; mode++) {
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
