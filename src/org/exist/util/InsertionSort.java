package org.exist.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.exist.dom.NodeProxy;

/**
	This class implements a version 
	of the insertion sort algorithm.

	The implementation is inspired on
	the work of Michael Maniscalco in
	C++
	http://www.michael-maniscalco.com/sorting.htm
	
	@author José María Fernández
*/
public final class InsertionSort {
	public final static void sortByNodeId(NodeProxy[] a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  return;
		
		// Second case, at least two elements
		if (a[lo0].compareTo(a[lo0 + 1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  return;
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			NodeProxy temp=a[i+1];
			if (temp.compareTo(a[i]) < 0) {
				int j;
				
				for(j=i;j>=lo0 && temp.compareTo(a[j]) < 0;j--) {
					a[j+1]=a[j];
				}
				a[j+1]=temp;
			}
		}
	}
	
	public final static void sort(Comparable[] a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  return;
		
		// Second case, at least two elements
		if(a[lo0].compareTo(a[lo0+1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  return;
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			Comparable temp = a[i+1];
			if(temp.compareTo(a[i]) < 0) {
				int j;
				
				for(j=i;j>=lo0 && temp.compareTo(a[j]) < 0;j--) {
					a[j+1]=a[j];
				}
				a[j+1]=temp;
			}
		}
	}

	public final static void sort(Comparable[] a, int lo0, int hi0, int b[])
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  return;
		
		// Second case, at least two elements
		if(a[lo0].compareTo(a[lo0+1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
			if(b!=null)  SwapVals.swap(b,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  return;
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			Comparable tempa = a[i+1];
			if(tempa.compareTo(a[i]) < 0) {
				int j;
				// Avoiding warnings
				int tempb=0;
				
				if(b!=null)  tempb=b[i+1];
				for(j=i;j>=lo0 && tempa.compareTo(a[j]) < 0;j--) {
					a[j+1]=a[j];
					if(b!=null)  b[j+1]=b[j];
				}
				a[j+1]=tempa;
				if(b!=null)  b[j+1]=tempb;
			}
		}
	}

	public final static void sort(Object[] a, Comparator comp, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  return;
		
		// Second case, at least two elements
		if(comp.compare(a[lo0],a[lo0+1]) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  return;
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			Object temp = a[i+1];
			if(comp.compare(temp,a[i]) < 0) {
				int j;
				
				for(j=i;j>=lo0 && comp.compare(temp,a[j]) < 0;j--) {
					a[j+1]=a[j];
				}
				a[j+1]=temp;
			}
		}
	}
		
	public final static void sort(List a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  return;
		
		// Second case, at least two elements
		if(((Comparable)a.get(lo0)).compareTo(a.get(lo0+1)) > 0) {
			SwapVals.swap(a,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  return;
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			Comparable temp = (Comparable) a.get(i+1);
			if(temp.compareTo(a.get(i)) < 0) {
				int j;
				
				for(j=i;j>=lo0 && temp.compareTo(a.get(j)) < 0;j--) {
					a.set(j+1,a.get(j));
				}
				a.set(j+1,temp);
			}
		}
	}

	public final static void sort(long a[], int lo0, int hi0, Object b[])
	//------------------------------------------------------------
	{
		// First case, no element or only one!
		if(lo0>=hi0)  return;
		
		// Second case, at least two elements
		if(a[lo0] > a[lo0+1]) {
			SwapVals.swap(a,lo0,lo0+1);
			if(b!=null)  SwapVals.swap(b,lo0,lo0+1);
		}
		
		// 2b, just two elements
		if(lo0+1 == hi0)  return;
		
		// Last case, the general one
		for (int i = lo0 + 1; i < hi0; i++) {
			long tempa=a[i+1];
			if(tempa<a[i]) {
				int j;
				Object tempb=null;
				
				if(b!=null)  tempb=b[i+1];
				for(j=i;j>=lo0 && tempa<a[j];j--) {
					a[j+1]=a[j];
					if(b!=null)  b[j+1]=b[j];
				}
				a[j+1]=tempa;
				if(b!=null)  b[j+1]=tempb;
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		List l = new ArrayList();
		
		if(args.length==0) {
			String[] a=new String[] {
				"Rudi",
				"Herbert",
				"Anton",
				"Berta",
				"Olga",
				"Willi",
				"Heinz" };
		
			for (int i = 0; i < a.length; i++)
				l.add(a[i]);
		} else {
			System.err.println("Ordering file "+args[0]+"\n");
			try {
				java.io.BufferedReader is=new java.io.BufferedReader(new java.io.FileReader(args[0]));
				String rr;
				
				while((rr=is.readLine())!=null) {
					l.add(rr);
				}
				
				is.close();
			} catch(Exception e) {
			}
		}
		long a;
		long b;
		a=System.currentTimeMillis();
		sort(l, 0, l.size() - 1);
		b=System.currentTimeMillis();
		System.err.println("Ellapsed time: "+(b-a)+" size: "+l.size());
		for (int i = 0; i < l.size(); i++)
			System.out.println(l.get(i));
	}
}
