/* Cortado - a video player java applet
 * Copyright (C) 2004 Fluendo S.L.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Street #330, Boston, MA 02111-1307, USA.
 */

package com.fluendo.plugin;

import java.awt.*;
import java.io.*;
import java.util.*;
import com.jcraft.jogg.*;
import com.fluendo.player.*;
import com.fluendo.utils.*;

public class MultiPartPlugin extends Plugin 
{
  private DataConsumer audioConsumer;
  private DataConsumer videoConsumer;
  private InputStream inputStream;
  private Component component;
  private Vector streams;

  private String boundary = "--ThisRandomString";
  private MultiPartParser mpp;

  private boolean stopping;
  
  class MultiPartStream {
    public String mime;
    DataConsumer consumer;

    public MultiPartStream (String m) {
      mime = m;
    }
  }

  public MultiPartPlugin ()
  {
    super(Plugin.TYPE_DEMUX);
  }

  public String getMime ()
  {
    return "multipart/x-mixed-replace";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    return Plugin.RANK_NONE;
  }

  public void initDemuxer (InputStream is, Component comp, DataConsumer ac, DataConsumer vc) {
    inputStream = is;
    audioConsumer = ac;
    videoConsumer = vc;
    component = comp;

    streams = new Vector();
    stopping = false;

    Debug.log(Debug.INFO, "started multipart reader");

    mpp = new MultiPartParser(is, boundary);
  }

  public boolean demux() throws IOException 
  {
    Vector v = mpp.readHeaders();
    if (v == null)
      return true;
      
    for (Enumeration e = v.elements(); e.hasMoreElements();) {
      String header = (String) e.nextElement();
      if (header == null)
        continue;
      
      header = header.toLowerCase();
      if (header.startsWith("content-type: ")) {
        String mime;
        MultiPartStream stream = null;

	mime = header.substring(14).trim();

        for (int i=0; i<streams.size(); i++) {
          stream = (MultiPartStream) streams.elementAt(i);
          if (stream.mime.equals(mime))
            break;
          stream = null;
        }
        if (stream == null) {
          Debug.log(Debug.INFO, "new stream "+mime);
          stream = new MultiPartStream(mime);
          streams.addElement(stream);
      
	  Plugin plugin = Plugin.makeByMime(mime);
	  if (plugin != null) {
	    plugin.initDecoder(component);

	    if (plugin.type == Plugin.TYPE_AUDIO) {
  	      if (audioConsumer != null) {
  	        audioConsumer.setPlugin(plugin);
	      }  
  	      stream.consumer = audioConsumer;
	    }
	    else if (plugin.type == Plugin.TYPE_VIDEO) {
  	      if (videoConsumer != null) {
  	        videoConsumer.setPlugin(plugin);
	      }  
  	      stream.consumer = videoConsumer;
 	    }
            else {
	      throw new RuntimeException ("unkown plugin type");
	    }
          }
          else {
	    throw new RuntimeException ("unkown mime type");
          }
	}
	if (stream.consumer != null) {
	  mpp.fillData(stream.consumer);
	}
      }
    }
    return !stopping;
  }

  public void stop()
  {
    stopping = true;
  }

}
