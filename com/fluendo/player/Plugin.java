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

  public static final Plugin make(byte[] data, int offset, int length)
  {
    int best = 0;
    Plugin bestPlugin = null;

    for (Enumeration e = plugins.elements(); e.hasMoreElements();) {
      Plugin plugin = (Plugin) e.nextElement();

      int rank = plugin.typeFind (data, offset, length);
      if (rank > best) {
        best = rank;
	bestPlugin = plugin;
      }
    }
    if (bestPlugin != null) {
      Class cl = bestPlugin.getClass();
      
      try {
        bestPlugin = (Plugin) cl.newInstance();
	System.out.println("create plugin: "+bestPlugin);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
    return bestPlugin;
  }

  public abstract int typeFind (byte[] data, int offset, int length);

  public void initDecoder(Component comp)
  {
    System.out.println("plugin not decoder");
  }

  public void initDemuxer (InputStream is, Component comp, DataConsumer ac, DataConsumer vc) 
  {
    System.out.println("plugin not demuxer");
  }

  public Image decodeVideo(byte[] data, int offset, int length)
  {
    System.out.println("plugin not video");
    return null;
  }
  public byte[] decodeAudio(byte[] data, int offset, int length)
  {
    System.out.println("plugin not audio");
    return null;
  }
  public boolean demux() throws IOException
  {
    System.out.println("plugin not demuxer");
    return false;
  }

  public abstract void stop();
}
