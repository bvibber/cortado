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

public class OggPlugin extends Plugin 
{
  private static final int BUFFSIZE = 4096;
  //private static final int BUFFSIZE = 8192;

  private DataConsumer audioConsumer;
  private DataConsumer videoConsumer;
  private InputStream inputStream;
  private Component component;

  private SyncState oy;
  private Vector streams;
  private Page og;
  private Packet op;

  private boolean stopping;
  
  class OggStream {
    public int serialno;
    public StreamState os;
    public boolean bos;
    DataConsumer consumer;

    public OggStream (int serial) {
      serialno = serial;
      os = new StreamState();
      os.init(serial);
      os.reset();
      bos = true;
    }
  }

  public OggPlugin ()
  {
    super(Plugin.TYPE_DEMUX);
  }

  public String getMime ()
  {
    return "application/ogg";
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

    oy = new SyncState();
    streams = new Vector();
    stopping = false;

    og = new Page();
    op = new Packet();
  }

  public boolean demux() throws IOException {
    int res;
    int index = oy.buffer(BUFFSIZE);
    //System.out.println("reading "+index+" "+BUFFSIZE);
    int read = inputStream.read(oy.data, index, BUFFSIZE);
    //System.out.println("read "+read);
    if (read < 0)
      return false;
    oy.wrote(read);
  
    while (!stopping) {
      res = oy.pageout(og);
      //System.out.println("pageout "+res);
      if (res == 0)
	break; // need more data
      if(res == -1) { 
	// missing or corrupt data at this page position
        // no reason to complain; already complained above
      }
      else {
	int serial = og.serialno();
	OggStream stream = null;
	for (int i=0; i<streams.size(); i++) {
	  stream = (OggStream) streams.elementAt(i);
	  if (stream.serialno == serial)
	    break;
	  stream = null;
	}
	if (stream == null) {
  	  Debug.log(Debug.INFO, "new stream "+serial);
	  stream = new OggStream(serial);
	  streams.addElement(stream);
	}

	//System.out.println("pagein");
        res = stream.os.pagein(og);
        if (res < 0) {
          // error; stream version mismatch perhaps
          System.err.println("Error reading first page of Ogg bitstream data.");
          return false;
        }
	while (!stopping) {
	  res = stream.os.packetout(op);
	  //System.out.println("packetout "+res);
          if(res == 0)
	    break; // need more data
          if(res == -1) { 
	    // missing or corrupt data at this page position
            // no reason to complain; already complained above
	    Debug.log(Debug.WARNING, "ogg error: packetout gave "+res);
          }
          else {
            // we have a packet.  Decode it
	    if (stream.bos) {
	      Plugin plugin;

	      plugin = Plugin.makeTypeFind(op.packet_base, op.packet, op.bytes);
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
	        throw new RuntimeException ("unkown stream type");
	      }
	      stream.bos = false;
	    }
	    //System.out.println("granulepos "+op.granulepos+" consume "+stream.consumer);
	    if (stream.consumer != null) {
	      MediaBuffer data = MediaBuffer.create();

	      data.copyData(op.packet_base, op.packet, op.bytes);
	      data.time_offset = op.granulepos;
	      data.timestamp = -1;
	      
	      stream.consumer.consume(data);
	    }
          }
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
