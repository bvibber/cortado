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

package com.fluendo.player;

import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.*;
import com.fluendo.plugin.*;

public abstract class Plugin
{
  public static final int TYPE_AUDIO = 1;
  public static final int TYPE_VIDEO = 2;
  public static final int TYPE_DEMUX = 3;

  public static final int RANK_NONE = 0;
  public static final int RANK_MAYBE = 10;
  public static final int RANK_PRIMARY = 20;

  public int type;

  /* timestamp of last data */
  public long last_pts;

  /* video */
  public int fps_numerator;
  public int fps_denominator;
  public int aspect_numerator;
  public int aspect_denominator;

  /* audio */
  public int channels;
  public int rate;

  private static Vector plugins = new Vector();

  static {
    loadPlugins();
  }

  public static void loadPlugins()
  {
    try {
      InputStream is = Plugin.class.getResourceAsStream("plugins.ini");
      if (is == null) {
        is = Plugin.class.getResourceAsStream("/plugins.ini");
      }
      if (is != null) {
        DataInputStream dis = new DataInputStream (is); 

        do {
	  String str = dis.readLine();
	  if (str == null)
	    break;
	  Class cl = Class.forName(str);

          System.out.println("registered plugin: "+str);
	  Plugin pl = (Plugin) cl.newInstance();
	  plugins.addElement(pl);
	}
	while (true);
      }
      else {
        System.out.println("could not register plugins");
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Plugin(int t) {
    type = t;
  }
  private static final Plugin dup (Plugin plugin) {
    Plugin result = null;

    Class cl = plugin.getClass();
    try {
      result = (Plugin) cl.newInstance();
      System.out.println("create plugin: "+plugin);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return result;
  }

  public static final Plugin makeTypeFind(byte[] data, int offset, int length)
  {
    int best = -1;
    Plugin result = null;

    for (Enumeration e = plugins.elements(); e.hasMoreElements();) {
      Plugin plugin = (Plugin) e.nextElement();

      int rank = plugin.typeFind (data, offset, length);
      if (rank > best) {
        best = rank;
	result = plugin;
      }
    }
    if (result != null) {
      result = dup (result);
    }
    return result;
  }

  public static final Plugin makeByMime(String mime)
  {
    Plugin result = null;

    for (Enumeration e = plugins.elements(); e.hasMoreElements();) {
      Plugin plugin = (Plugin) e.nextElement();

      if (mime.equals(plugin.getMime())) {
        result = dup (plugin);
        break;
      }
    }
    return result;
  }

  public abstract String getMime ();
  public abstract int typeFind (byte[] data, int offset, int length);

  public long offsetToTime(long ts_offset)
  {
    System.out.println("offsetToTime not implemented");
    return -1;
  }

  public void initDecoder(Component comp)
  {
    System.out.println("plugin not decoder");
  }

  public void initDemuxer (InputStream is, Component comp, DataConsumer ac, DataConsumer vc) 
  {
    System.out.println("plugin not demuxer");
  }

  public MediaBuffer decode(MediaBuffer buffer) 
  {
    System.out.println("plugin not decoder");
    return null;
  }

  public boolean demux() throws IOException
  {
    System.out.println("plugin not demuxer");
    return false;
  }

  public abstract void stop();
}
