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

package com.fluendo.plugin;

import java.util.*;
import com.jcraft.jogg.*;
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public class OggDemux extends Element
{
  private SyncState oy;
  private Vector streams;
  private Page og;
  private Packet op;

  class OggStream extends Pad {
    public int serialno;
    public StreamState os;
    public boolean bos;
    private boolean needTypefind;

    public OggStream (int serial) {
      super (Pad.SRC, "serial_"+serial);

      serialno = serial;
      os = new StreamState();
      os.init(serial);
      os.reset();
      bos = true;
      needTypefind = true;
    }
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      return sinkpad.pushEvent (event);
    }
  }

  private Pad sinkpad = new Pad(Pad.SINK, "sink") {
   
    private OggStream findStream (int serial) {
      OggStream stream = null;
      for (int i=0; i<streams.size(); i++) {
	stream = (OggStream) streams.elementAt(i);
	if (stream.serialno == serial)
	  break;
	stream = null;
      }
      return stream;
    }
	  
    private boolean forwardEvent (com.fluendo.jst.Event event)
    {
      for (int i=0; i<streams.size(); i++) {
	OggStream stream = (OggStream) streams.elementAt(i);
	stream.pushEvent (event);
      }
      return true;
    }
    private void resetStreams ()
    {
      for (int i=0; i<streams.size(); i++) {
	OggStream stream = (OggStream) streams.elementAt(i);
	stream.os.reset();
      }
    }

    protected boolean eventFunc (com.fluendo.jst.Event event)
    {
      switch (event.getType()) {
        case Event.FLUSH_START:
	  forwardEvent (event);
	  synchronized (streamLock) {
            Debug.log(Debug.INFO, "synced "+this);
	  }
	  break;
        case Event.FLUSH_STOP:
	  synchronized (streamLock) {
	    oy.reset();
	    resetStreams();
	    forwardEvent (event);
	  }
	  break;
        case Event.NEWSEGMENT:
	  synchronized (streamLock) {
	    forwardEvent (event);
	  }
	  break;
        case Event.EOS:
	  synchronized (streamLock) {
	    forwardEvent (event);
	  }
	  break;
        default:
	  forwardEvent (event);
	  break;
      }
      return true;
    }
    protected int chainFunc (com.fluendo.jst.Buffer buf)
    {
      int res;
      int flowRet = OK;

      int index = oy.buffer(buf.length);

      System.arraycopy(buf.data, buf.offset, oy.data, index, buf.length);
      oy.wrote(buf.length);
  
      while (flowRet == OK) {
        res = oy.pageout(og);
        if (res == 0)
	  break; // need more data
        if(res == -1) { 
	  // missing or corrupt data at this page position
          // no reason to complain; already complained above
        }
        else {
	  int serial = og.serialno();
	  OggStream stream = findStream (serial);
	  boolean needTypefind = false;
	  if (stream == null) {
	    stream = new OggStream(serial);
	    streams.addElement(stream);
	  }
          res = stream.os.pagein(og);
          if (res < 0) {
            // error; stream version mismatch perhaps
            System.err.println("Error reading first page of Ogg bitstream data.");
            postMessage (Message.newError (this, "Error reading first page of Ogg bitstream data."));
            return ERROR;
          }
	  while (flowRet == OK) {
	    res = stream.os.packetout(op);
            if(res == 0)
	      break; // need more data
            if(res == -1) { 
	      // missing or corrupt data at this page position
              // no reason to complain; already complained above
	      Debug.log(Debug.WARNING, "ogg error: packetout gave "+res);
            }
            else {
	      if (stream != null) {
	        if (stream.needTypefind) {
	          String mime = ElementFactory.typeFindMime (op.packet_base, op.packet, op.bytes);
  	          Debug.log(Debug.INFO, "new stream "+serial+", mime "+mime);

		  stream.needTypefind = false;
		  stream.setCaps (new Caps (mime));
                  addPad (stream);
	        }

	        com.fluendo.jst.Buffer data = com.fluendo.jst.Buffer.create();

	        data.copyData(op.packet_base, op.packet, op.bytes);
	        data.time_offset = op.granulepos;
	        data.timestamp = -1;
	      
	        flowRet = stream.push(data);
	      }
            }
          }
        }
      }
      return flowRet;
    }
    protected boolean activateFunc (int mode) 
    {
      if (mode == MODE_NONE) {
	oy.reset();
	resetStreams();
      }
      return true;
    }
  };

  public String getName ()
  {
    return "oggdemux";
  }
  public String getMime ()
  {
    return "application/ogg";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    return -1;
  }

  public OggDemux () {
    super ();

    oy = new SyncState();
    streams = new Vector();

    og = new Page();
    op = new Packet();

    addPad (sinkpad);
  }
}
