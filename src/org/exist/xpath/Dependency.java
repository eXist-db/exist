package org.exist.xpath;

public class Dependency {

	public final static int NO_DEPENDENCY = 0;
	public final static int CONTEXT_SET = 1;
	public final static int CONTEXT_ITEM = 2;
	public final static int LOCAL_VARS = 4;
	
	public final static int DEFAULT_DEPENDENCIES = CONTEXT_SET;

}
