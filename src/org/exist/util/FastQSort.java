package org.exist.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.exist.dom.NodeProxy;

public final class FastQSort {

	private final static void QuickSort(Comparable a[], int l, int r)
	//----------------------------------------------------
	{
		int M = 4;
		int i;
		int j;
		Comparable v;

		if ((r - l) > M) {
			// 26july00: following [.][1] -> [.][0]
			i = (r + l) / 2;
			if (a[l].compareTo(a[i]) > 0)
				swap(a, l, i); // Tri-Median Methode!
			if (a[l].compareTo(a[r]) > 0)
				swap(a, l, r);
			if (a[i].compareTo(a[r]) > 0)
				swap(a, i, r);

			j = r - 1;
			swap(a, i, j);
			i = l;

			v = a[j];
			for (;;) {
				while (a[++i].compareTo(v) < 0);
				while (a[--j].compareTo(v) > 0);
				if (j < i)
					break;
				swap(a, i, j);
			}
			swap(a, i, r - 1);

			QuickSort(a, l, j);
			QuickSort(a, i + 1, r);
		}
	}

	private final static void QuickSort(Object a[], Comparator comp, int l, int r)
	//----------------------------------------------------
	{
		int M = 4;
		int i;
		int j;
		Object v;

		if ((r - l) > M) {
			// 26july00: following [.][1] -> [.][0]
			i = (r + l) / 2;
			if (comp.compare(a[l], a[i]) > 0)
				swap(a, l, i); // Tri-Median Methode!
			if (comp.compare(a[l], a[r]) > 0)
				swap(a, l, r);
			if (comp.compare(a[i], a[r]) > 0)
				swap(a, i, r);

			j = r - 1;
			swap(a, i, j);
			i = l;

			v = a[j];
			for (;;) {
				while (comp.compare(a[++i], v) < 0);
				while (comp.compare(a[--j], v) > 0);
				if (j < i)
					break;
				swap(a, i, j);
			}
			swap(a, i, r - 1);

			QuickSort(a, comp, l, j);
			QuickSort(a, comp, i + 1, r);
		}
	}

	private final static void QuickSort(NodeProxy a[], int l, int r)
	//----------------------------------------------------
	{
		int M = 4;
		int i;
		int j;
		NodeProxy v;

		if ((r - l) > M) {
			// 26july00: following [.][1] -> [.][0]
			i = (r + l) / 2;
			if (a[l].compareTo(a[i]) > 0)
				swap(a, l, i); // Tri-Median Methode!
			if (a[l].compareTo(a[r]) > 0)
				swap(a, l, r);
			if (a[i].compareTo(a[r]) > 0)
				swap(a, i, r);

			j = r - 1;
			swap(a, i, j);
			i = l;

			v = a[j];
			for (;;) {
				while (a[++i].compareTo(v) < 0);
				while (a[--j].compareTo(v) > 0);
				if (j < i)
					break;
				swap(a, i, j);
			}
			swap(a, i, r - 1);

			QuickSort(a, l, j);
			QuickSort(a, i + 1, r);
		}
	}

	private final static void QuickSort(List a, int l, int r)
	//----------------------------------------------------
	{
		int M = 4;
		int i;
		int j;
		Object v;

		if ((r - l) > M) {
			// 26july00: following [.][1] -> [.][0]
			i = (r + l) / 2;
			if (((Comparable) a.get(l)).compareTo(a.get(i)) > 0)
				swap(a, l, i); // Tri-Median Methode!
			if (((Comparable) a.get(l)).compareTo(a.get(r)) > 0)
				swap(a, l, r);
			if (((Comparable) a.get(i)).compareTo(a.get(r)) > 0)
				swap(a, i, r);

			j = r - 1;
			swap(a, i, j);
			i = l;

			v = a.get(j);
			for (;;) {
				while (((Comparable) a.get(++i)).compareTo(v) < 0);
				while (((Comparable) a.get(--j)).compareTo(v) > 0);
				if (j < i)
					break;
				swap(a, i, j);
			}
			swap(a, i, r - 1);

			QuickSort(a, l, j);
			QuickSort(a, i + 1, r);
		}
	}

	private final static void QuickSort(long a[], int l, int r, Object b[])
	//----------------------------------------------------
	{
		int M = 4;
		int i;
		int j;
		long v;

		if ((r - l) > M) {
			// 26july00: following [.][1] -> [.][0]
			i = (r + l) / 2;
			if (a[l] > a[i]) {
				swap(a, l, i); // Tri-Median Methode!
				swap(b, l, i);
			}
			if (a[l] > a[r]) {
				swap(a, l, r);
				swap(b, l, r);
			}
			if (a[i] > a[r]) {
				swap(a, i, r);
				swap(b, i, r);
			}

			j = r - 1;
			swap(a, i, j);
			swap(b, i, j);
			i = l;

			v = a[j];
			for (;;) {
				while (a[++i] < v);
				while (a[--j] > v);
				if (j < i)
					break;
				swap(a, i, j);
				swap(b, i, j);
			}
			swap(a, i, r - 1);
			swap(b, i, r - 1);

			QuickSort(a, l, j, b);
			QuickSort(a, i + 1, r, b);
		}
	}

	private final static void QuickSortByNodeId(NodeProxy a[], int l, int r)
	//----------------------------------------------------
	{
		int M = 4;
		int i;
		int j;
		NodeProxy v;

		if ((r - l) > M) {
			// 26july00: following [.][1] -> [.][0]
			i = (r + l) / 2;
			if (a[l].gid > a[i].gid)
				swap(a, l, i); // Tri-Median Methode!
			if (a[l].gid > a[r].gid)
				swap(a, l, r);
			if (a[i].gid > a[r].gid)
				swap(a, i, r);

			j = r - 1;
			swap(a, i, j);
			i = l;

			v = a[j];
			for (;;) {
				while (a[++i].gid < v.gid);
				while (a[--j].gid > v.gid);
				if (j < i)
					break;
				swap(a, i, j);
			}
			swap(a, i, r - 1);

			QuickSortByNodeId(a, l, j);
			QuickSortByNodeId(a, i + 1, r);
		}
	}

	private final static void swap(long a[], int i, int j) {
		long T = a[i];
		a[i] = a[j];
		a[j] = T;
	}

	private final static void swap(Object[] a, int i, int j) {
		if (a == null)
			return;
		Object T = a[i];
		a[i] = a[j];
		a[j] = T;
	}

	private final static void swap(List a, int i, int j)
	//-----------------------------------------------
	{
		Object T;

		T = a.get(i);
		a.set(i, a.get(j));
		a.set(j, T);
	}

	private final static void swap(Comparable[] a, int i, int j)
	//-----------------------------------------------
	{
		Comparable T;

		T = a[i];
		a[i] = a[j];
		a[j] = T;
	}

	private final static void InsertionSortByNodeId(NodeProxy[] a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		int i, j;
		NodeProxy temp = null;

		for (i = lo0 + 1; i <= hi0; i++) {
			temp = a[i]; // the column we're sorting on
			j = i;

			while ((j > lo0) && (a[j - 1].gid > temp.gid)) {
				a[j] = a[j - 1];
				j--;
			}

			a[j] = temp;
		}
	}

	private final static void InsertionSort(Comparable[] a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		int i, j;
		Comparable temp = null;

		for (i = lo0 + 1; i <= hi0; i++) {
			temp = a[i]; // the column we're sorting on
			j = i;

			while ((j > lo0) && (a[j - 1].compareTo(temp) > 0)) {
				a[j] = a[j - 1];
				j--;
			}

			a[j] = temp;
		}
	}

	private final static void InsertionSort(Object[] a, Comparator comp, int lo0, int hi0)
		//------------------------------------------------------------
		{
			int i, j;
			Object temp = null;

			for (i = lo0 + 1; i <= hi0; i++) {
				temp = a[i]; // the column we're sorting on
				j = i;

				while ((j > lo0) && (comp.compare(a[j - 1], temp) > 0)) {
					a[j] = a[j - 1];
					j--;
				}

				a[j] = temp;
			}
		}
		
	private final static void InsertionSort(List a, int lo0, int hi0)
	//------------------------------------------------------------
	{
		int i, j;
		Object temp = null;

		for (i = lo0 + 1; i <= hi0; i++) {
			temp = a.get(i); // the column we're sorting on
			j = i;

			while ((j > lo0) && (((Comparable) a.get(j - 1)).compareTo(temp) > 0)) {
				a.set(j, a.get(j - 1));
				j--;
			}

			a.set(j, temp);
		}
	}

	private final static void InsertionSort(long a[], int lo0, int hi0, Object b[])
	//------------------------------------------------------------
	{
		int i, j;
		long tempa;
		Object tempb = null;

		for (i = lo0 + 1; i <= hi0; i++) {
			tempa = a[i]; // the column we're sorting on
			if (b != null)
				tempb = b[i];
			j = i;

			while ((j > lo0) && (a[j - 1] > tempa)) {
				a[j] = a[j - 1];
				if (b != null)
					b[j] = b[j - 1];
				j--;
			}
			a[j] = tempa;
			if (b != null)
				b[j] = tempb;
		}
	}

	public static void sort(Comparable[] a, int lo, int hi) {
		QuickSort(a, lo, hi);
		InsertionSort(a, lo, hi);
	}

	public static void sort(Object[] a, Comparator c, int lo, int hi) {
		QuickSort(a, c, lo, hi);
		InsertionSort(a, c, lo, hi);
	}
	
	public static void sort(List a, int lo, int hi) {
		QuickSort(a, lo, hi);
		InsertionSort(a, lo, hi);
	}

	public static void sort(NodeProxy[] a, int lo, int hi) {
		QuickSort(a, lo, hi);
		InsertionSort(a, lo, hi);
	}

	public static void sortByNodeId(NodeProxy[] a, int lo, int hi) {
		QuickSortByNodeId(a, lo, hi);
		InsertionSortByNodeId(a, lo, hi);
	}

	public static void sort(long[] a, int lo, int hi, Object b[]) {
		QuickSort(a, lo, hi, b);
		InsertionSort(a, lo, hi, b);
	}

	public static void main(String[] args) throws Exception {
		String[] a =
			new String[] {
				"Rudi",
				"Herbert",
				"Anton",
				"Berta",
				"Olga",
				"Willi",
				"Heinz" };
		List l = new ArrayList(a.length);
		for (int i = 0; i < a.length; i++)
			l.add(a[i]);
		sort(l, 0, l.size() - 1);
		for (int i = 0; i < l.size(); i++)
			System.out.println(l.get(i));
	}
}
