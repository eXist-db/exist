package org.exist.util;

import java.util.ArrayList;
import java.util.List;

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

	public static void sort(Comparable[] a, int lo, int hi) {
		QuickSort(a, lo, hi);
		InsertionSort(a, lo, hi);
	}

	public static void sort(List a, int lo, int hi) {
		QuickSort(a, lo, hi);
		InsertionSort(a, lo, hi);
	}
	
	public static void main(String[] args) throws Exception {
		String[] a = new String[] { "Rudi", "Herbert", "Anton", "Berta", "Olga", "Willi", "Heinz" };
		List l = new ArrayList(a.length);
		for(int i = 0; i < a.length; i++)
			l.add(a[i]);
		sort(l, 0, l.size() - 1);
		for (int i = 0; i < l.size(); i++)
			System.out.println(l.get(i));
	}
}
