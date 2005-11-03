/* Copyright (C) <2004> Wim Taymans <wim@fluendo.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package com.fluendo.jst;

import java.io.*;
import java.util.*;
import com.fluendo.utils.*;

public class ElementFactory
{
  private static Vector elements = new Vector();
  static {
    loadElements();
  }

  public static void loadElements()
  {
    try {
      InputStream is = ElementFactory.class.getResourceAsStream("plugins.ini");
      if (is == null) {
        is = ElementFactory.class.getResourceAsStream("/plugins.ini");
      }
      if (is != null) {
        DataInputStream dis = new DataInputStream (is); 

        do {
	  String str = dis.readLine();
	  if (str == null)
	    break;
	  Class cl = Class.forName(str);

          Debug.log(Debug.INFO, "registered plugin: "+str);
	  Element pl = (Element) cl.newInstance();
	  elements.addElement(pl);
	}
	while (true);
      }
      else {
        Debug.log(Debug.INFO, "could not register plugins");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static final Element dup (Element element) {
    Element result = null;

    Class cl = element.getClass();
    try {
      result = (Element) cl.newInstance();
      Debug.log(Debug.INFO, "create element: "+element);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public static final Element makeTypeFind(byte[] data, int offset, int length)
  {
    int best = -1;
    Element result = null;

    for (Enumeration e = elements.elements(); e.hasMoreElements();) {
      Element element = (Element) e.nextElement();

      int rank = element.typeFind (data, offset, length);
      if (rank > best) {
        best = rank;
	result = element;
      }
    }
    if (result != null) {
      result = dup (result);
    }
    return result;
  }

  public static final Element makeByMime(String mime)
  {
    Element result = null;

    for (Enumeration e = elements.elements(); e.hasMoreElements();) {
      Element element = (Element) e.nextElement();

      if (mime.equals(element.getMime())) {
        result = dup (element);
        break;
      }
    }
    return result;
  }

  public static final Element makeByName(String name)
  {
    Element result = null;

    for (Enumeration e = elements.elements(); e.hasMoreElements();) {
      Element element = (Element) e.nextElement();

      if (name.equals(element.getName())) {
        result = dup (element);
        break;
      }
    }
    return result;
  }
}
