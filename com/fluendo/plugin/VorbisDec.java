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
import com.jcraft.jorbis.*;
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public class VorbisDec extends Element 
{
  private long packet;
  private long offset;
  private Info vi;
  private Comment vc;
  private DspState vd;
  private Block vb;

  private Packet op;
  private float[][][] _pcmf = new float[1][][];
  private int[] _index;

  private Pad srcpad = new Pad(Pad.SRC, "src") {
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      return sinkpad.pushEvent(event);
    }
  };

  private Pad sinkpad = new Pad(Pad.SINK, "sink") {
    private Vector queue = new Vector();

    private void clearQueue ()
    {
      for (Enumeration e = queue.elements(); e.hasMoreElements();) {
        java.lang.Object obj = e.nextElement();

        if (obj instanceof com.fluendo.jst.Buffer) {
          ((com.fluendo.jst.Buffer)obj).free();
        }
      }
    }

    private int pushOutput (com.fluendo.jst.Buffer buf) {
      long toffset = buf.time_offset;
      int ret = OK;

      if (toffset == -1) {
	queue.addElement (buf);
      }
      else {
        /* patch buffers */
	if (queue.size() > 0) {
          for (int i = queue.size()-1; i >= 0; i--) {
	    com.fluendo.jst.Buffer qbuf = 
	      (com.fluendo.jst.Buffer) queue.elementAt(i);

            toffset -= qbuf.length / (2 * vi.channels);
	    qbuf.time_offset = toffset;
	    qbuf.timestamp = toffset * Clock.SECOND / vi.rate;
	  }
          for (int i = 0; i < queue.size(); i++) {
	    com.fluendo.jst.Buffer qbuf = 
	       (com.fluendo.jst.Buffer) queue.elementAt(i);
	    if (ret == OK)
              ret = srcpad.push(qbuf);
	    else
              qbuf.free();
	  }
	  queue.setSize(0);
	}
	if (ret == OK)
          ret = srcpad.push(buf);
      }
      return ret;
    }

    protected boolean eventFunc (com.fluendo.jst.Event event) {
      boolean result;

      switch (event.getType()) {
        case Event.FLUSH_START:
          result = srcpad.pushEvent(event);
	  synchronized (streamLock) {
	    Debug.log(Debug.INFO, "synced "+this);
	  }
	  break;
        case Event.FLUSH_STOP:
	  synchronized (streamLock) {
            result = srcpad.pushEvent(event);
	    clearQueue();
	    offset = -1;
	    vd.synthesis_init(vi);
	  }
	  break;
        default:
	  synchronized (streamLock) {
            result = srcpad.pushEvent(event);
	  }
	  break;
      }
      return result;
    }
    protected int chainFunc (com.fluendo.jst.Buffer buf) {
      int result = OK;

      //System.out.println ("creating packet");
      op.packet_base = buf.data;
      op.packet = buf.offset;
      op.bytes = buf.length;
      op.b_o_s = (packet == 0 ? 1 : 0);
      op.e_o_s = 0;
      op.packetno = packet;
      op.granulepos = buf.time_offset;

      if (packet < 3) {
        //System.out.println ("decoding header");
        if(vi.synthesis_headerin(vc, op) < 0){
	  // error case; not a vorbis header
	  Debug.log(Debug.ERROR, "This Ogg bitstream does not contain Vorbis audio data.");
	  return ERROR;
        }
        if (packet == 2) {
	  vd.synthesis_init(vi);
	  vb.init(vd);

	  Debug.log(Debug.INFO, "vorbis rate: "+vi.rate);
	  Debug.log(Debug.INFO, "vorbis channels: "+vi.channels);

          _index =new int[vi.channels];

	  caps = new Caps ("audio/raw");
	  caps.setFieldInt ("width", 16);
	  caps.setFieldInt ("depth", 16);
	  caps.setFieldInt ("rate", vi.rate);
	  caps.setFieldInt ("channels", vi.channels);
        }
        buf.free();
        packet++;

	return OK;
      }
      else {
        if ((op.packet_base[op.packet] & 1) == 1) {
          Debug.log(Debug.INFO, "ignoring header");
	  return OK;
	}

        int samples;
        if (vb.synthesis(op) == 0) { // test for success!
          vd.synthesis_blockin(vb);
        }
        else {
          Debug.log(Debug.ERROR, "decoding error");
	  return ERROR;
        }
        //System.out.println ("decode vorbis done");
        while ((samples = vd.synthesis_pcmout (_pcmf, _index)) > 0) {
          float[][] pcmf=_pcmf[0];
	  int numbytes = samples * 2 * vi.channels;
	  int k = 0;

	  buf.ensureSize(numbytes);
	  buf.offset = 0;
	  buf.time_offset = offset;
	  buf.length = numbytes;
	  buf.caps = caps;

	  //System.out.println(vi.rate + " " +target+ " " +samples);

          for (int j=0; j<samples; j++){
            for (int i=0; i<vi.channels; i++) {
	       int val = (int) (pcmf[i][_index[i]+j] * 32767.0);
	       if (val > 32767)
	         val = 32767;
	       else if (val < -32768)
	         val = -32768;

               buf.data[k] = (byte) ((val >> 8) & 0xff);
               buf.data[k+1] = (byte) (val & 0xff);
	       k+=2;
	    }
          }
          //System.out.println ("decoded "+samples+" samples");
          vd.synthesis_read(samples);

          if (offset != -1) {
	    buf.timestamp = offset * Clock.SECOND / vi.rate;
	    offset += samples;
	  }

          if ((result = pushOutput(buf)) != OK)
            break;
        }
      }
      packet++;

      if (op.granulepos != -1) {
        offset = op.granulepos;
      }

      return result;
    }
  };

  public VorbisDec() {
    super();

    vi = new Info();
    vc = new Comment();
    vd = new DspState();
    vb = new Block(vd);
    op = new Packet();

    addPad (srcpad);
    addPad (sinkpad);
  }

  protected int changeState (int transition) {
    int res;

    switch (transition) {
      case STOP_PAUSE:
        packet = 0;
	offset = -1;
        vi.init();
        vc.init();
        break;
      default:
        break;
    }

    res = super.changeState (transition);

    return res;
  }


  public String getName ()
  {
    return "vorbisdec";
  }
  public String getMime ()
  {
    return "audio/x-vorbis";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    if (data[offset+1] == 0x76) {
      return 10;
    }
    return -1;
  }

  public long offsetToTime (long ts_offset) {
    if (ts_offset == -1) {
      return -1;
    }
    return ts_offset * 1000 / vi.rate;
  }

}
