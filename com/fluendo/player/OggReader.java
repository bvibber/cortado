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

package com.fluendo.player;

import com.fluendo.utils.*;

import java.awt.*;
import java.io.*;
import java.util.*;
import com.jcraft.jogg.*;

public class OggReader implements Runnable 
{
  //private static final int BUFFSIZE = 4096;
  private static final int BUFFSIZE = 8192;

  private AudioConsumer audioConsumer;
  private VideoConsumer videoConsumer;
  private InputStream inputStream;

  private SyncState oy;
  private Vector streams;
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

  public OggReader (InputStream is, AudioConsumer ac, VideoConsumer vc) {
    inputStream = is;
    audioConsumer = ac;
    videoConsumer = vc;

    oy = new SyncState();
    streams = new Vector();
    stopping = false;
  }

  public void run() {
    int res;
    Page og;
    Packet op;

    System.out.println("started ogg reader");

    og = new Page();
    op = new Packet();

    try {
      while (!stopping) {
        int index = oy.buffer(BUFFSIZE);
        //System.out.println("reading "+index+" "+BUFFSIZE);
        int read = inputStream.read(oy.data, index, BUFFSIZE);
        //System.out.println("read "+read);
        if (read < 0)
          break;
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
  	      System.out.println("new stream "+serial);
	      stream = new OggStream(serial);
	      streams.addElement(stream);
	    }

	    //System.out.println("pagein");
            res = stream.os.pagein(og);
            if (res < 0) {
              // error; stream version mismatch perhaps
              System.err.println("Error reading first page of Ogg bitstream data.");
              return;
            }
	    while (!stopping) {
	      res = stream.os.packetout(op);
	      //System.out.println("packetout "+res);
              if(res == 0)
	        break; // need more data
              if(res == -1) { 
	        // missing or corrupt data at this page position
                // no reason to complain; already complained above
              }
              else {
                // we have a packet.  Decode it
	        if (stream.bos) {
	          // typefind
	          if (op.packet_base[op.packet+1] == 0x76) {
		    System.out.println ("found vorbis audio");
  	  	    if (audioConsumer != null)
  	              audioConsumer.setType("audio/x-vorbis");
  	            stream.consumer = audioConsumer;
	            //System.out.println("audio "+stream.consumer);
	          }
	          else if (op.packet_base[op.packet+1] == 0x73) {
		    System.out.println ("found smoke video");
	  	    if (videoConsumer  != null)
	              videoConsumer.setType("image/x-smoke");
	            stream.consumer = videoConsumer;
	            //System.out.println("video "+stream.consumer);
	          }
	          else if (op.packet_base[op.packet+1] == 0x74) {
		    System.out.println ("found theora video");
	  	    if (videoConsumer  != null)
	              videoConsumer.setType("video/x-theora");
	            stream.consumer = videoConsumer;
		  }
	          stream.bos = false;
	        }
	        //System.out.println("consume "+stream.consumer);
	        if (stream.consumer != null)
	          stream.consumer.consume(op.packet_base, op.packet, op.bytes);
	      }
  	    }
	  }
	}
      }
    }
    catch (Exception e) {
      e.printStackTrace();
      stopping = true;
    }
    
    System.out.println("ogg reader done");
  }
  public void stop() {
    stopping = true;
  }
}
