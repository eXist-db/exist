package org.exist.security;

import java.io.IOException;
import org.exist.storage.io.VariableByteInput;
import java.util.Random;
import org.exist.storage.io.VariableByteOutputStream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.easymock.classextension.EasyMock;
import static org.easymock.classextension.EasyMock.expect;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;

/**
 *
 * @author aretter
 */
public class UnixStylePermissionTest {

    @Test
    public void writeRead_roundtrip() throws IOException {

        final SecurityManager mockSecurityManager = EasyMock.createMock(SecurityManager.class);

        final Account mockOwner = EasyMock.createMock(Account.class);
        final int ownerId = new Random().nextInt();
        final Group mockOwnerGroup = EasyMock.createMock(Group.class);
        final int ownerGroupId = new Random().nextInt();
        final int mode = 644;

        final VariableByteOutputStream mockOstream = EasyMock.createMock(VariableByteOutputStream.class);
        final VariableByteInput mockIstream = EasyMock.createMock(VariableByteInput.class);

        final Account mockOwner2 = EasyMock.createMock(Account.class);
        final Group mockOwnerGroup2 = EasyMock.createMock(Group.class);
        final int mode2 = 588;

        //constructor logic expectations
        expect(mockSecurityManager.getSystemSubject()).andReturn(null);
        expect(mockSecurityManager.getDBAGroup()).andReturn(null);

        //write(ostream) expectations
        expect(mockOwner.getId()).andReturn(ownerId);
        mockOstream.writeInt(ownerId);
        expect(mockOwnerGroup.getId()).andReturn(ownerGroupId);
        mockOstream.writeInt(ownerGroupId);
        mockOstream.writeInt(mode);

        //read(istream) expectations
        expect(mockIstream.readInt()).andReturn(ownerId);
        expect(mockSecurityManager.getAccount(ownerId)).andReturn(mockOwner2);
        expect(mockIstream.readInt()).andReturn(ownerGroupId);
        expect(mockSecurityManager.getGroup(ownerGroupId)).andReturn(mockOwnerGroup2);
        expect(mockIstream.readInt()).andReturn(mode2);

        replay(mockSecurityManager, mockOwner, mockOwnerGroup, mockOstream, mockIstream);

        Permission permission = new TestableUnixStylePermission(mockSecurityManager, mockOwner, mockOwnerGroup, mode);
        permission.write(mockOstream);
        permission.read(mockIstream);

        verify(mockSecurityManager, mockOwner, mockOwnerGroup, mockOstream, mockIstream);

        //test read(...) set values
        assertEquals(permission.getOwner(), mockOwner2);
        assertEquals(permission.getGroup(), mockOwnerGroup2);
        assertEquals(permission.getMode(), mode2);

    }


    public class TestableUnixStylePermission extends UnixStylePermission {
        public TestableUnixStylePermission(SecurityManager sm, Account owner, Group ownerGroup, int mode) {
            super(sm);

            //set values directly
            this.owner = owner;
            this.ownerGroup = ownerGroup;
            setMode(mode);
        }
    }
}
