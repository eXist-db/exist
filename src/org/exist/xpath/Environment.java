
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang Meier (wolfgang@exist-db.org)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import java.util.HashMap;

public class Environment {

  protected HashMap functions = new HashMap();

  public Environment(String[][] functionNames) {
    for(int i = 0; i < functionNames.length; i++)
      functions.put(functionNames[i][0], functionNames[i][1]);
  }

  public String getFunction(String name) {
    String func = (String)functions.get(name);
    return func == null ? name : func;
  }

  public boolean hasFunction(String name) {
    return functions.containsKey(name);
  }
}
