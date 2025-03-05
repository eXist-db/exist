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
package org.exist.security.internal;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.digests.GeneralDigest;
import org.bouncycastle.crypto.digests.MD5Digest;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.exist.security.Account;
import org.exist.security.Credential;
import org.exist.security.MessageDigester;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author <a href="mailto:adam.retter@gmail.com">Adam Retter</a>
 *
 */
public class Password implements Credential {
    
    public enum Hash {
        MD5,
        RIPEMD160
    }
    

    //private
    private final String pw;
    private final String digestPw;
    
    public final static Hash DEFAULT_ALGORITHM = Hash.RIPEMD160;
    private final Hash algorithm;
    
    final Pattern ptnHash = Pattern.compile("\\{([A-Z0-9]+)\\}(.*)");

    public Password(Account account, String password) {
        
        if (password == null) {
            this.algorithm = DEFAULT_ALGORITHM;
            this.pw = null;
            this.digestPw = null;
        } else{
            final Matcher mtcHash = ptnHash.matcher(password);
            
            if (mtcHash.matches()) {
                this.algorithm = Hash.valueOf(mtcHash.group(1));
                this.pw = mtcHash.group(2);
            } else {
                this.algorithm = DEFAULT_ALGORITHM;
                this.pw = hashAndEncode(password);
            }
            
            this.digestPw = digest(account.getName(), account.getRealmId(), pw);
        }
    }
    
    public Password(final Account account, final Hash algorithm, final String encodedHash) {
        this.algorithm = algorithm;
        this.pw = encodedHash;
        this.digestPw = digest(account.getName(), account.getRealmId(), pw);
    }

    @Override
    public String getDigest() {
        return digestPw;
    }
    
    final String digest(String username, String realmId, String p) {
        return MessageDigester.byteArrayToHex(hash(username + ":" + realmId + ":" + p));
    }

    final String hashAndEncode(String p) {
        //base64 encode the hash
        return Base64.encodeBase64String(hash(p));
    }
    
    final byte[] hash(String p) {

        return switch (algorithm) {
            case RIPEMD160 -> ripemd160Hash(p);
            case MD5 -> md5Hash(p);
            default -> null;
        };
    }
    
    final byte[] ripemd160Hash(final String p) {
        //ripemd160 hash
        return digest(p, new RIPEMD160Digest());
    }
    
    final byte[] md5Hash(final String p) {
        return digest(p, new MD5Digest());
    }

    private static byte[] digest(final String s, final GeneralDigest generalDigest) {
        final byte[] data = s.getBytes();
        generalDigest.update(data, 0, data.length);

        final byte[] digest = new byte[generalDigest.getDigestSize()];
        generalDigest.doFinal(digest, 0);
        return digest;
    }
    

    @Override
    public boolean check(Object credentials) {
    	
    	if (credentials == this) {
            return true;
        }
    	
    	//workaround old style, remove -shabanovd 
    	if(credentials == null) {
    		credentials = "";
        }
    	
    	if(credentials instanceof Password || credentials instanceof String) {
            return equals(credentials);
        }
    	
    	if(credentials instanceof char[]) {
            return equals(String.valueOf((char[]) credentials));
        }
    	
    	return false;
    }

    @Override
    public boolean equals(Object obj) {
    	
    	if(obj == this) {
            return true;
        }

        switch (obj) {
            case null -> {
                return false;
            }
            case Password p -> {

                if (algorithm != p.algorithm) {
                    throw new RuntimeException("Cannot compare passwords with different algorithms i.e. " + algorithm + " and " + p.algorithm);
                }

                return (Objects.equals(pw, p.pw));
            }
            case String s -> {
                return (hashAndEncode(s)).equals(pw);
            }
            default -> {
            }
        }

        return false;
    }
    
    @Override
    public String toString() {
    	return "{" + algorithm + "}" + pw;
    }
}
