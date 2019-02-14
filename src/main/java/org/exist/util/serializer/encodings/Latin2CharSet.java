/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util.serializer.encodings;

/**
 * Latin2 character set. Adopted from Saxon (http://saxon.sourceforge.net).
 */
public class Latin2CharSet extends CharacterSet {

	protected final static CharacterSet instance = new Latin2CharSet();

	private static boolean[] c = null;

	static {
		c = new boolean[750];

		for (int i = 0; i < 127; i++) {
			c[i] = true;
		}
		for (int i = 128; i < 750; i++) {
			c[i] = false;
		}

		c[160] = true;
		c[164] = true;
		c[167] = true;
		c[168] = true;
		c[173] = true;
		c[176] = true;
		c[180] = true;
		c[184] = true;
		c[193] = true;
		c[194] = true;
		c[196] = true;
		c[199] = true;
		c[201] = true;
		c[203] = true;
		c[205] = true;
		c[206] = true;
		c[211] = true;
		c[212] = true;
		c[214] = true;
		c[215] = true;
		c[218] = true;
		c[220] = true;
		c[221] = true;
		c[223] = true;
		c[225] = true;
		c[226] = true;
		c[228] = true;
		c[231] = true;
		c[233] = true;
		c[235] = true;
		c[237] = true;
		c[238] = true;
		c[243] = true;
		c[244] = true;
		c[246] = true;
		c[247] = true;
		c[250] = true;
		c[252] = true;
		c[253] = true;
		c[258] = true;
		c[259] = true;
		c[260] = true;
		c[261] = true;
		c[262] = true;
		c[263] = true;
		c[268] = true;
		c[269] = true;
		c[270] = true;
		c[271] = true;
		c[272] = true;
		c[273] = true;
		c[280] = true;
		c[281] = true;
		c[282] = true;
		c[283] = true;
		c[313] = true;
		c[314] = true;
		c[317] = true;
		c[318] = true;
		c[321] = true;
		c[322] = true;
		c[323] = true;
		c[324] = true;
		c[327] = true;
		c[328] = true;
		c[336] = true;
		c[337] = true;
		c[340] = true;
		c[341] = true;
		c[344] = true;
		c[345] = true;
		c[346] = true;
		c[347] = true;
		c[350] = true;
		c[351] = true;
		c[352] = true;
		c[353] = true;
		c[354] = true;
		c[355] = true;
		c[356] = true;
		c[357] = true;
		c[366] = true;
		c[367] = true;
		c[368] = true;
		c[369] = true;
		c[377] = true;
		c[378] = true;
		c[379] = true;
		c[380] = true;
		c[381] = true;
		c[382] = true;
		c[711] = true;
		c[728] = true;
		c[729] = true;
		c[731] = true;
		c[733] = true;

	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.encodings.CharacterSet#inCharacterSet(char)
	 */
	public boolean inCharacterSet(char ch) {
		return (ch < 750 && c[ch]);
	}

	public static CharacterSet getInstance() {
		return instance;
	}
}
