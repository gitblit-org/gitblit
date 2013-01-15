package com.gitblit.utils;

import java.io.Serializable;
import java.util.Comparator;

/**
 * A comparator for {@link java.util.TreeSet} that sorts strings ascending inside the {@link java.util.TreeSet}  
 * 
 * @author saheba
 *
 */
public class StringComparator implements Comparator<String>, Serializable {
	private static final long serialVersionUID = 7563266118711225424L;

	/* (non-Javadoc)
	 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
	 */
	@Override
	public int compare(String o1, String o2) {
		// TODO Auto-generated method stub
		return o1.compareTo(o2);
	}

}
