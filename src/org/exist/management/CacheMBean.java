package org.exist.management;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Jun 9, 2007
 * Time: 8:51:10 PM
 * To change this template use File | Settings | File Templates.
 */
public interface CacheMBean {

    public String getType();
    
    public int getSize();

    public int getUsed();

    public int getHits();

    public int getFails();

    public String getFileName();
}