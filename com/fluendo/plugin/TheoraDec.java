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

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import com.jcraft.jogg.*;
import com.fluendo.jheora.*;
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public class TheoraDec extends Element 
{
  private Info ti;
  private Comment tc;
  private State ts;
  private Packet op;
  private int packet;
  private YUVBuffer yuv;
  private Caps caps;

  private long lastTs;
  private boolean needKeyframe;

  private Pad srcpad = new Pad(Pad.SRC, "src") {
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      return sinkpad.pushEvent(event);
    }
  };

  private Pad sinkpad = new Pad(Pad.SINK, "sink") {
    private Vector queue = new Vector();

    private void clearQueue ()
    {
      for (Enumeration e = queue.elements(); e.hasMoreElements();)
      {
        java.lang.Object obj = e.nextElement();

	if (obj instanceof com.fluendo.jst.Buffer) {
	  ((com.fluendo.jst.Buffer)obj).free();
	}
      }
    }

    private int pushOutput (com.fluendo.jst.Buffer buf) {
      long newTime = buf.timestamp;

      if (newTime == -1) {
        queue.addElement (buf);
      }
      else {
        int size = queue.size();
        /* patch buffers */
        if (size > 0) {
          for (int i = 0; i < size; i++) {
            long time;

            com.fluendo.jst.Buffer qbuf =
                (com.fluendo.jst.Buffer) queue.elementAt(i);
      
            time = newTime - ((((long)(size-i)) * 1000000 * ti.fps_denominator) / ti.fps_numerator);

            qbuf.timestamp = time;
            srcpad.push(qbuf);
          }
          queue.setSize(0);
        }
        return srcpad.push(buf);
      }
      return OK;
    }
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      boolean result;

      switch (event.getType()) {
        case com.fluendo.jst.Event.FLUSH_START:
	  result = srcpad.pushEvent (event);
	  synchronized (streamLock) {
	    Debug.log(Debug.INFO, this+" synced");
            clearQueue();
            lastTs = -1;
	    needKeyframe = true;
	  }
          break;
        case com.fluendo.jst.Event.FLUSH_STOP:
	  synchronized (streamLock) {
            result = srcpad.pushEvent(event);
            lastTs = -1;
	    needKeyframe = true;
	  }
          break;
        case com.fluendo.jst.Event.EOS:
        case com.fluendo.jst.Event.NEWSEGMENT:
	default:
	  synchronized (streamLock) {
            result = srcpad.pushEvent(event);
	  }
          break;
      }
      return result;
    }

    protected int chainFunc (com.fluendo.jst.Buffer buf) {
      int result;

      op.packet_base = buf.data;
      op.packet = buf.offset;
      op.bytes = buf.length;
      op.b_o_s = (packet == 0 ? 1 : 0);
      op.e_o_s = 0;
      op.packetno = packet;
      op.granulepos = buf.time_offset;
    
      if (packet < 3) {
        //System.out.println ("decoding header");
        if (ti.decodeHeader(tc, op) < 0){
          buf.free();
          // error case; not a theora header
          Debug.log(Debug.ERROR, "does not contain Theora video data.");
          return ERROR;
        }
        if (packet == 2) {
          ts.decodeInit(ti);
    
          Debug.log(Debug.INFO, "theora dimension: "+ti.width+"x"+ti.height);
          if (ti.aspect_denominator == 0) {
            ti.aspect_numerator = 1;
            ti.aspect_denominator = 1;
          }
          Debug.log(Debug.INFO, "theora offset: "+ti.offset_x+","+ti.offset_y);
          Debug.log(Debug.INFO, "theora frame: "+ti.frame_width+","+ti.frame_height);
          Debug.log(Debug.INFO, "theora aspect: "+ti.aspect_numerator+"/"+ti.aspect_denominator);
          Debug.log(Debug.INFO, "theora framerate: "+ti.fps_numerator+"/"+ti.fps_denominator);

	  caps = new Caps ("video/raw");
	  caps.setFieldInt ("width", ti.frame_width);
	  caps.setFieldInt ("height", ti.frame_height);
	  caps.setFieldInt ("aspect_x", ti.aspect_numerator);
	  caps.setFieldInt ("aspect_y", ti.aspect_denominator);
        }
        buf.free();
        packet++;

	return OK;
      }
      else {
        if ((op.packet_base[op.packet] & 0x80) == 0x80) {
          Debug.log(Debug.INFO, "ignoring header");
          return OK;
        }

        if (needKeyframe) {
          if (ts.isKeyframe(op)) {
	    needKeyframe = false;
	  }
        }

	if (op.granulepos != -1) {
	  lastTs = offsetToTime(op.granulepos);
	}
	else if (lastTs != -1) {
	  lastTs += (1000000 * ti.fps_denominator) / ti.fps_numerator;
	}
	else {
	  lastTs = -1;
	}

	if (!needKeyframe) {
	  try{
            if (ts.decodePacketin(op) != 0) {
              buf.free();
              Debug.log(Debug.ERROR, "Error Decoding Theora.");
	      postMessage (Message.newError (this, "Error decoding Theora"));
              return ERROR;
            }
            if (ts.decodeYUVout(yuv) != 0) {
              buf.free();
	      postMessage (Message.newError (this, "Error getting the Theora picture"));
              Debug.log(Debug.ERROR, "Error getting the picture.");
              return ERROR;
	    }
            buf.object = yuv.getObject(ti.offset_x, ti.offset_y, ti.frame_width, ti.frame_height);
	    buf.caps = caps;
	    buf.timestamp = lastTs;
            result = pushOutput(buf);
          }
	  catch (Exception e) {
	    e.printStackTrace();
	    postMessage (Message.newError (this, e.getMessage()));
            result = ERROR;
	  }
	}
        else {
          result = OK;
	  buf.free();
	}
      }
      packet++;

      return result;
    }
  };

  public TheoraDec() {
    super();

    ti = new Info();
    tc = new Comment();
    ts = new State();
    yuv = new YUVBuffer();
    op = new Packet();

    addPad (srcpad);
    addPad (sinkpad);

    lastTs = -1;
    packet = 0;
    needKeyframe = true;
  }

  public String getName ()
  {
    return "theoradec";
  }
  public String getMime ()
  {
    return "video/x-theora";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    if (data[offset+1] == 0x74) {
      return 10;
    }
    return -1;
  }

  public long offsetToTime (long offset) {
    return (long) (ts.granuleTime(offset) * 1000000);
  }
}
