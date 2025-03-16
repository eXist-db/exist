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
package org.exist.dom.persistent;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.UUIDGenerator;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Class representing a locktoken. Introduced for webDAV locking.
 *
 * @author Dannes Wessels
 */
public class LockToken {

    // Lock type
    private final LockType type;

    // Lock depth
    private final LockDepth depth;

    // Lock scope
    private final LockScope scope;

    // Timeout
    public static final long LOCK_TIMEOUT_INFINITE = -1L;
    public static final long NO_LOCK_TIMEOUT = -2L;

    // Write Locks and Null Resources
    // see http://www.webdav.org/specs/rfc2518.html#rfc.section.7.4
    private final ResourceType resourceType; //= ResourceType.NOT_SPECIFIED;

    // Other
    @Nullable private String owner = null;
    private long timeout = -1L;
    @Nullable private String token;

    public LockToken(final LockType type, final LockDepth depth, final LockScope scope, @Nullable final String owner,
        final long timeout, @Nullable final String token, final ResourceType resourceType) {
        this.type = type;
        this.depth = depth;
        this.scope = scope;
        this.owner = owner;
        this.timeout = timeout;
        this.token = token;
        this.resourceType = resourceType;
    }

    // Getters and setters

    /**
     * @return the type of lock.
     */
    public LockType getType() {
        return type;
    }

    public LockDepth getDepth() {
        return depth;
    }

    public LockScope getScope() {
        return scope;
    }

    @Nullable public String getOwner() {
        return owner;
    }

    public void setOwner(@Nullable final String owner) {
        this.owner = owner;
    }

    public long getTimeOut() {
        return timeout;
    }

    public void setTimeOut(final long timeout) {
        this.timeout = timeout;
    }

    @Nullable public String getOpaqueLockToken() {
        return token;
    }

    public ResourceType getResourceType() {
        return resourceType;
    }

    public boolean isNullResource() {
        return resourceType == ResourceType.NULL_RESOURCE;
    }

    // Create new UUID for token
    public void createOpaqueLockToken() {
        token = LockToken.generateUUID();
    }

    // Helper function.
    public static String generateUUID() {
        return UUIDGenerator.getUUID();
    }

    public void write(final VariableByteOutputStream ostream) throws IOException {
        // TODO(AR) these 3 bytes could be encoded into 1
        ostream.writeByte(type.getValue());
        ostream.writeByte(depth.getValue());
        ostream.writeByte(scope.getValue());

        ostream.writeUTF(owner != null ? owner : "");
        ostream.writeLong(timeout);
        ostream.writeUTF(token != null ? token : "");
        ostream.writeByte(resourceType.getValue());
    }

    public static LockToken read(final VariableByteInput istream) throws IOException {
        final LockType type = LockType.valueOf(istream.readByte());
        final LockDepth depth = LockDepth.valueOf(istream.readByte());
        final LockScope scope = LockScope.valueOf(istream.readByte());

        String owner = istream.readUTF();
        if(owner.isEmpty()) {
            owner = null;
        }

        final long timeout = istream.readLong();

        String token = istream.readUTF();
        if(token.isEmpty()) {
            token = null;
        }

        final ResourceType resourceType = ResourceType.valueOf(istream.readByte());
        return new LockToken(type, depth, scope, owner, timeout, token, resourceType);
    }

    public enum LockType {
        NONE((byte)0x0),
        WRITE((byte)0x1),
        NOT_SET((byte)0x4);

        private final byte value;

        LockType(final byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static LockType valueOf(final byte value) {
            for (final LockType lockType : LockType.values()) {
                if (lockType.getValue() == value) {
                    return lockType;
                }
            }
            throw new IllegalArgumentException("No LockType for value: " + value);
        }
    }

    public enum LockDepth {
        ZERO((byte)0x8),
        ONE((byte)0x9),
        INFINITY((byte)0x10),
        NOT_SET((byte)0x11);

        private final byte value;

        LockDepth(final byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static LockDepth valueOf(final byte value) {
            for (final LockDepth lockDepth : LockDepth.values()) {
                if (lockDepth.getValue() == value) {
                    return lockDepth;
                }
            }
            throw new IllegalArgumentException("No LockDepth for value: " + value);
        }
    }

    public enum LockScope {
        NONE((byte)0x16),
        EXCLUSIVE((byte)0x17),
        SHARED((byte)0x18),
        NOT_SET((byte)0x19);

        private final byte value;

        LockScope(final byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static LockScope valueOf(final byte value) {
            for (final LockScope lockScope : LockScope.values()) {
                if (lockScope.getValue() == value) {
                    return lockScope;
                }
            }
            throw new IllegalArgumentException("No LockScope for value: " + value);
        }
    }

    public enum ResourceType {
        NOT_SPECIFIED((byte)0x0),
        NULL_RESOURCE((byte)0x1);

        private final byte value;

        ResourceType(final byte value) {
            this.value = value;
        }

        public byte getValue() {
            return value;
        }

        public static ResourceType valueOf(final byte value) {
            for (final ResourceType resourceType : ResourceType.values()) {
                if (resourceType.getValue() == value) {
                    return resourceType;
                }
            }
            throw new IllegalArgumentException("No ResourceType for value: " + value);
        }
    }
}
