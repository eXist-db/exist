/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-05 The eXist Project
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
package org.exist.util;

import java.util.List;

/**
	This class only contains static
	methods which help when the values
	of two positions in a array or
	list-like structure must be swapped.
	
	Based on previous implementations
	found in eXist FastQSort original
	code and internet
*/
public final class SwapVals {
	public final static void swap(long a[], int i, int j)
	//-----------------------------------------------
	{
		long T = a[i];
		a[i] = a[j];
		a[j] = T;
	}

	public final static void swap(int a[], int i, int j)
	//-----------------------------------------------
	{
		int T = a[i];
		a[i] = a[j];
		a[j] = T;
	}

	public final static void swap(Object[] a, int i, int j)
	//-----------------------------------------------
	{
//		assert a != null : "Trying to swap elements in a null array!";
		
		Object T = a[i];
		a[i] = a[j];
		a[j] = T;
	}

	public final static <C> void swap(List<C> a, int i, int j)
	//-----------------------------------------------
	{
		C T;

		T = a.get(i);
		a.set(i, a.get(j));
		a.set(j, T);
	}
//
//	public final static <C extends Comparable<C>>void swap(C[] a, int i, int j)
//	//-----------------------------------------------
//	{
//		Comparable T;
//
//		T = a[i];
//		a[i] = a[j];
//		a[j] = T;
//	}
}
