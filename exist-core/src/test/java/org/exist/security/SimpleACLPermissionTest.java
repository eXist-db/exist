package org.exist.security;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.storage.DBBroker;
import org.exist.storage.io.VariableByteInputStream;

import java.io.IOException;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.Database;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.internal.SecurityManagerImpl;
import java.util.Random;
import org.easymock.EasyMock;
import org.exist.util.ByteArray;
import org.exist.util.io.FastByteArrayInputStream;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class SimpleACLPermissionTest {

    private final static int ALL = Permission.READ | Permission.WRITE | Permission.EXECUTE;

    @Test
    public void add() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        
        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        assertEquals(0, permission.getACECount());

        final int userId = 1;
        final int mode = ALL;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, userId, mode);
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        assertEquals(1, permission.getACECount());
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(userId, permission.getACEId(0));
        assertEquals(mode, permission.getACEMode(0));
    }

    @Test
    public void addACE_ForUserWithModeString() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        final Account mockAccount = EasyMock.createMock(Account.class);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        assertEquals(0, permission.getACECount());

        final int userId = 1112;
        final String userName = "aretter";
        final String mode = "rwx";

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true);

        expect(mockSecurityManager.getAccount(userName)).andReturn(mockAccount);
        expect(mockAccount.getId()).andReturn(userId);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockAccount);

        permission.addACE(ACE_ACCESS_TYPE.ALLOWED, ACE_TARGET.USER, userName, mode);

        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockAccount);

        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));
    }

    @Test
    public void addACE_ForGroupWithModeString() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        final Group mockGroup = EasyMock.createMock(Group.class);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        assertEquals(0, permission.getACECount());

        final int groupId = 1112;
        final String groupName = "aretter";
        final String mode = "rwx";

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true);
        
        expect(mockSecurityManager.getGroup(groupName)).andReturn(mockGroup);
        expect(mockGroup.getId()).andReturn(groupId);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockGroup);

        permission.addACE(ACE_ACCESS_TYPE.ALLOWED, ACE_TARGET.GROUP, groupName, mode);

        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockGroup);

        assertEquals(1, permission.getACECount());
        assertEquals(groupId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.GROUP, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));
        assertEquals(mode, permission.getACEModeString(0));
    }

    @Test
    public void insert_atFront_whenEmpty() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true);
        
        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        assertEquals(0, permission.getACECount());

        final int userId = 1112;
        final int mode = ALL;

        permission.insertUserACE(0, ACE_ACCESS_TYPE.ALLOWED, userId, mode);
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));
    }

    @Test
    public void insert_atFront() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(2);
        
        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        assertEquals(0, permission.getACECount());

        final int userId = 1112;
        final int mode = ALL;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, userId, mode);
        
        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));

        final int secondUserId = 1113;
        final int secondMode = 04;
        permission.insertUserACE(0, ACE_ACCESS_TYPE.ALLOWED, secondUserId, secondMode);
        
        assertEquals(2, permission.getACECount());
        assertEquals(secondUserId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(secondMode, permission.getACEMode(0));
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void insert_inMiddle() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(3);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(3);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(3);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(3);
        
        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        final int userId = 1112;
        final int mode = ALL;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, userId, mode);
        
        assertEquals(1, permission.getACECount());
        
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));

        final int secondUserId = 1113;
        final int secondMode = 04;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, secondUserId, secondMode);
        
        assertEquals(2, permission.getACECount());
        
        assertEquals(secondUserId, permission.getACEId(1));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(1));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(1));
        assertEquals(secondMode, permission.getACEMode(1));

        final int thirdUserId = 1114;
        final int thirdMode = 02;
        permission.insertUserACE(1, ACE_ACCESS_TYPE.ALLOWED, thirdUserId, thirdMode);
        
        assertEquals(3, permission.getACECount());
        
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));

        assertEquals(thirdUserId, permission.getACEId(1));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(1));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(1));
        assertEquals(thirdMode, permission.getACEMode(1));

        assertEquals(secondUserId, permission.getACEId(2));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(2));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(2));
        assertEquals(secondMode, permission.getACEMode(2));
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test(expected=PermissionDeniedException.class)
    public void insert_atEnd() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(3);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(3);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(3);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(3);
        
        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        final int userId = 1112;
        final int mode = ALL;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, userId, mode);
        
        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ALL, permission.getACEMode(0));

        final int secondUserId = 1113;
        final int secondMode = 04;
        permission.insertUserACE(1, ACE_ACCESS_TYPE.ALLOWED, secondUserId, secondMode);
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void remove_firstACE() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(3);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(3);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(3);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(3);
        
        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, 1, ALL);
        final int secondUserId = 2;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, secondUserId, ALL);
        assertEquals(2, permission.getACECount());

        permission.removeACE(0);
        assertEquals(1, permission.getACECount());
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(secondUserId, permission.getACEId(0));
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void remove_middleACE() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(4);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(4);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(4);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(4);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        final int firstUserId = 1;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, firstUserId, ALL);
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, 2, ALL);
        final int thirdUserId = 3;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, thirdUserId, ALL);
        assertEquals(3, permission.getACECount());

        permission.removeACE(1);
        assertEquals(2, permission.getACECount());
        assertEquals(firstUserId, permission.getACEId(0));
        assertEquals(thirdUserId, permission.getACEId(1));
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void remove_lastACE() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(3);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(3);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(3);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(3);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        final int firstUserId = 1;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, firstUserId, ALL);
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, 2, ALL);
        assertEquals(2, permission.getACECount());

        permission.removeACE(1);
        assertEquals(1, permission.getACECount());
        assertEquals(firstUserId, permission.getACEId(0));
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void modify() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(3);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(3);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(3);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(3);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        final int userId = 1;
        final int mode = Permission.READ;
        final ACE_ACCESS_TYPE access_type = ACE_ACCESS_TYPE.ALLOWED;
        permission.addUserACE(access_type, userId, mode);

        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(access_type, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(mode, permission.getACEMode(0));

        permission.modifyACE(0, access_type, Permission.WRITE);

        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(access_type, permission.getACEAccessType(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(Permission.WRITE, permission.getACEMode(0));

        permission.modifyACE(0, ACE_ACCESS_TYPE.DENIED, Permission.READ | Permission.WRITE);

        assertEquals(1, permission.getACECount());
        assertEquals(userId, permission.getACEId(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(ACE_ACCESS_TYPE.DENIED, permission.getACEAccessType(0));
        assertEquals(Permission.READ | Permission.WRITE, permission.getACEMode(0));
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void clear() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(3);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(3);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(3);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(3);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        assertEquals(0, permission.getACECount());

        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, 1, ALL);
        final int secondUserId = 2;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, secondUserId, ALL);
        assertEquals(2, permission.getACECount());

        permission.clear();

        assertEquals(0, permission.getACECount());
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
    }

    @Test
    public void validate_cant_read_when_readNotInACL() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0700;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        final Subject mockUser = EasyMock.createMock(Subject.class);
        final boolean mockUserHasDbaRole = false;
        final int mockUserId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);

        expect(mockUser.hasDbaRole()).andReturn(mockUserHasDbaRole);
        expect(mockUser.getId()).andReturn(mockUserId);
        expect(mockUser.getGroupIds()).andReturn(new int[0]);

        replay(mockSecurityManager, mockUser);

        Permission permission = new SimpleACLPermission(mockSecurityManager, ownerId, ownerGroupId, mode);

        boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockUser);

        assertFalse(isValid);
    }

    @Test
    public void validate_read_when_readInACL() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0700;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        final Subject mockUser = EasyMock.createMock(Subject.class);
        final boolean mockUserHasDbaRole = false;
        final int mockUserId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);

        expect(mockUser.hasDbaRole()).andReturn(mockUserHasDbaRole);
        expect(mockUser.getId()).andReturn(mockUserId);
        expect(mockUser.getGroupIds()).andReturn(new int[0]);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockUser);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, mockUserId, Permission.READ);

        boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockUser);

        assertTrue(isValid);
    }

    @Test
    public void validate_cant_read_ACL_ordered_entries() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(2);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0700;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        /**
         * We create ACE entries which has -
         * 1) user "userA" who is DENIED READ access
         * 2) group "groupA" who is ALLOWED READ access
         *
         * userA is a member of groupA, and so should still be DENIED read access
         * as the ACL is evaluated top-down
         *
         */
        final Subject mockUser = EasyMock.createMock(Subject.class);
        final boolean mockUserHasDbaRole = false;
        final int mockUserId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mockGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        expect(mockUser.hasDbaRole()).andReturn(mockUserHasDbaRole);
        expect(mockUser.getId()).andReturn(mockUserId);
        expect(mockUser.getGroupIds()).andReturn(new int[0]);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockUser);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager, ownerId, ownerGroupId, mode);
        
        permission.addUserACE(ACE_ACCESS_TYPE.DENIED, mockUserId, Permission.READ);
        permission.addGroupACE(ACE_ACCESS_TYPE.ALLOWED, mockGroupId, Permission.READ);

        boolean isValid = permission.validate(mockUser, Permission.READ);

        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockUser);

        assertFalse(isValid);
    }

    @Test
    public void validate_can_write_ACL_ordered_entries() throws PermissionDeniedException {
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(2);

        final int ownerId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mode = 0700;
        final int ownerGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        /**
         * We create ACE entries which has -
         * 1) user "userA" who is DENIED READ access
         * 2) group "groupA" who is ALLOWED WRITE access
         *
         * userA is a member of groupA, and so should still be ALLOWED write access
         * as the ACL is evaluated top-down
         *
         */
        final Subject mockUser = EasyMock.createMock(Subject.class);
        final boolean mockUserHasDbaRole = false;
        final int mockUserId = new Random().nextInt(SecurityManagerImpl.MAX_USER_ID);
        final int mockGroupId = new Random().nextInt(SecurityManagerImpl.MAX_GROUP_ID);

        expect(mockUser.hasDbaRole()).andReturn(mockUserHasDbaRole);
        expect(mockUser.getId()).andReturn(mockUserId);
        expect(mockUser.getGroupIds()).andReturn(new int[]{mockGroupId});

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockUser);

        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager, ownerId, ownerGroupId, mode);

        permission.addUserACE(ACE_ACCESS_TYPE.DENIED, mockUserId, Permission.READ);
        permission.addGroupACE(ACE_ACCESS_TYPE.ALLOWED, mockGroupId, Permission.WRITE);

        boolean isValid = permission.validate(mockUser, Permission.WRITE);

        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject, mockUser);

        assertTrue(isValid);
    }
    
    @Test
    public void roundtrip_write_read() throws PermissionDeniedException, IOException {
        
        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);
        final Database mockDatabase = EasyMock.createMock(Database.class);
        final DBBroker mockBroker = EasyMock.createMock(DBBroker.class);
        final Subject mockCurrentSubject = EasyMock.createMock(Subject.class);

        expect(mockSecurityManager.getDatabase()).andReturn(mockDatabase).times(2);
        expect(mockDatabase.getActiveBroker()).andReturn(mockBroker).times(2);
        expect(mockBroker.getCurrentSubject()).andReturn(mockCurrentSubject).times(2);
        expect(mockCurrentSubject.hasDbaRole()).andReturn(true).times(2);

        replay(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        SimpleACLPermission permission = new SimpleACLPermission(mockSecurityManager);
        
        assertEquals(0, permission.getACECount());

        final int userId1 = 1;
        final int mode1 = ALL;
        permission.addUserACE(ACE_ACCESS_TYPE.ALLOWED, userId1, mode1);
        
        final int groupId2 = 2;
        final int mode2 = Permission.READ;
        permission.addGroupACE(ACE_ACCESS_TYPE.DENIED, groupId2, mode2);
        
        final VariableByteOutputStream os = new VariableByteOutputStream();
        
        //write the acl out
        permission.write(os);
        
        verify(mockSecurityManager, mockDatabase, mockBroker, mockCurrentSubject);
        
        assertEquals(2, permission.getACECount());
        
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission.getACEAccessType(0));
        assertEquals(userId1, permission.getACEId(0));
        assertEquals(ACE_TARGET.USER, permission.getACETarget(0));
        assertEquals(mode1, permission.getACEMode(0));
        
        assertEquals(ACE_ACCESS_TYPE.DENIED, permission.getACEAccessType(1));
        assertEquals(groupId2, permission.getACEId(1));
        assertEquals(ACE_TARGET.GROUP, permission.getACETarget(1));
        assertEquals(mode2, permission.getACEMode(1));
        
        //get the written acl data
        ByteArray buf = os.data();
        byte data[] = new byte[buf.size()];
        buf.copyTo(data, 0);
        
        
        //create a new permission instance
        SimpleACLPermission permission2 = new SimpleACLPermission(mockSecurityManager);
        
        //read the acl in
        permission2.read(new VariableByteInputStream(new FastByteArrayInputStream(data)));
        
        assertEquals(2, permission2.getACECount());
        
        assertEquals(ACE_ACCESS_TYPE.ALLOWED, permission2.getACEAccessType(0));
        assertEquals(userId1, permission2.getACEId(0));
        assertEquals(ACE_TARGET.USER, permission2.getACETarget(0));
        assertEquals(mode1, permission2.getACEMode(0));
        
        assertEquals(ACE_ACCESS_TYPE.DENIED, permission2.getACEAccessType(1));
        assertEquals(groupId2, permission2.getACEId(1));
        assertEquals(ACE_TARGET.GROUP, permission2.getACETarget(1));
        assertEquals(mode2, permission2.getACEMode(1));
    }
}