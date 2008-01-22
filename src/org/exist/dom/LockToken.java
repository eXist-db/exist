/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */

package org.exist.dom;

import java.io.IOException;

import org.exist.security.UUIDGenerator;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

/**
 *  Class representing a locktoken. Introduced for webDAV locking.
 *
 * @author Dannes Wessels
 */
public class LockToken {
    
    // Lock type
    private byte type = LOCK_TYPE_NOT_SET;
    public final static byte LOCK_TYPE_NONE = 0;
    public final static byte LOCK_TYPE_WRITE = 1;
    public final static byte LOCK_TYPE_NOT_SET = 4;
    
    // Lock depth
    private byte depth = LOCK_DEPTH_NOT_SET;
    public final static byte LOCK_DEPTH_0 = 0;
    public final static byte LOCK_DEPTH_1 = 1;
    public final static byte LOCK_DEPTH_INFINIY = 2;
    public final static byte LOCK_DEPTH_NOT_SET = 4;
    
    // Lock scope
    private byte scope = LOCK_SCOPE_NOT_SET;
    public final static byte LOCK_SCOPE_NONE = 0;
    public final static byte LOCK_SCOPE_EXCLUSIVE = 1;
    public final static byte LOCK_SCOPE_SHARED = 2;
    public final static byte LOCK_SCOPE_NOT_SET = 4;
    
    // Timeout
    public final static long LOCK_TIMEOUT_INFINITE = -1L;
    
    // Write Locks and Null Resources
    // see http://www.webdav.org/specs/rfc2518.html#rfc.section.7.4
    private byte resourceType = RESOURCE_TYPE_NOT_SPECIFIED;
    public final static byte RESOURCE_TYPE_NOT_SPECIFIED = 0;
    public final static byte RESOURCE_TYPE_NULL_RESOURCE = 1;
    
    // Other
    private String owner = null;
    private long timeout = -1L;
    private String token = null;
    
    /**
     * Creates a new instance of LockToken
     */
    public LockToken() {
        // Left empty intentionally
    }
    
    
    // Getters and setters
    public byte getType(){  return type;   }
    public void setType(byte type){ this.type=type; }
    
    public byte getDepth(){  return depth;   }
    public void setDepth(byte depth){ this.depth=depth; }
    
    public byte getScope(){  return scope;   }
    public void setScope(byte scope){ this.scope=scope; }
    
    public String getOwner(){ return owner; }
    public void setOwner(String owner){ this.owner = owner; }
    
    public long getTimeOut(){ return timeout;   }
    public void setTimeOut(long timeout){ this.timeout=timeout; }
    
    public String getOpaqueLockToken(){ return token; }
    public void setOpaqueLockToken(String token){ this.token = token; }
    
    // 
    public byte getResourceType(){  return resourceType;   }
    public void setResourceType(byte type){ resourceType=type; }
    
    public boolean isNullResource(){
        return (resourceType == LockToken.RESOURCE_TYPE_NULL_RESOURCE );
    }
    
    // Create new UUID for token
    public void createOpaqueLockToken(){
        token = LockToken.generateUUID();
    }
    
    // Helper function.
    public static String generateUUID(){
        return UUIDGenerator.getUUID();
    }
    
    public void write(VariableByteOutputStream ostream) throws IOException {
        ostream.writeByte(type);
        ostream.writeByte(depth);
        ostream.writeByte(scope);
        ostream.writeUTF(owner != null ? owner : "");
        ostream.writeLong(timeout);
        ostream.writeUTF(token != null ? token : "");
        ostream.writeByte(resourceType);
    }
    
    public void read(VariableByteInput istream) throws IOException {
        type=istream.readByte();
        depth=istream.readByte();
        scope=istream.readByte();
        
        owner=istream.readUTF();
        if (owner.length() == 0){
            owner = null;
        }
        
        timeout = istream.readLong();
        
        token=istream.readUTF();
        if (token.length() == 0){
            token = null;
        }
        
        resourceType=istream.readByte();
    }
    
}
