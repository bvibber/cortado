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
import com.jcraft.jogg.*;
import com.jcraft.jorbis.*;
import com.fluendo.player.*;

public class VorbisPlugin extends Plugin 
{
  private int packet;
  private Info vi;
  private Comment vc;
  private DspState vd;
  private Block vb;

  private Packet op;
  private float[][][] _pcmf = new float[1][][];
  private int[] _index;

  private static final boolean ZEROTRAP=true;
  private static final short BIAS=0x84;
  private static final int CLIP=32635;
  private static final byte[] exp_lut =
    { 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,
      4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };

  public VorbisPlugin() {
    super(Plugin.TYPE_AUDIO);
  }

  public String getMime ()
  {
    return "audio/vorbis";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    if (data[offset+1] == 0x76) {
      return Plugin.RANK_PRIMARY;
    }
    return Plugin.RANK_NONE;
  }

  public void initDecoder(Component comp) {
    rate = 8000;    
    channels = 1;    
    packet = 0;

    vi = new Info();
    vc = new Comment();
    vd = new DspState();
    vb = new Block(vd);

    vi.init();
    vc.init();

    op = new Packet();
  }

  public long offsetToTime (long ts_offset) {
    if (ts_offset == -1) {
      return -1;
    }
    return ts_offset * 1000 / vi.rate;
  }

  public MediaBuffer decode(MediaBuffer buf)
  {
    MediaBuffer result = null;

    //System.out.println ("creating packet");
    op.packet_base = buf.data;
    op.packet = buf.offset;
    op.bytes = buf.length;
    op.b_o_s = (packet == 0 ? 1 : 0);
    op.e_o_s = 0;
    op.packetno = packet;

    if (packet < 3) {
      //System.out.println ("decoding header");
      if(vi.synthesis_headerin(vc, op) < 0){
	// error case; not a vorbis header
	System.err.println("This Ogg bitstream does not contain Vorbis audio data.");
	return null;
      }
      if (packet == 2) {
	vd.synthesis_init(vi);
	vb.init(vd);

	System.out.println("vorbis rate: "+vi.rate);
	System.out.println("vorbis channels: "+vi.channels);

	rate = vi.rate;
	channels = vi.channels;

        _index =new int[vi.channels];
      }
    }
    else {
      int samples;
      if (vb.synthesis(op) == 0) { // test for success!
        vd.synthesis_blockin(vb);
      }
      else {
        System.out.println ("decoding error");
      }
      //System.out.println ("decode vorbis done");
      while ((samples = vd.synthesis_pcmout (_pcmf, _index)) > 0) {
        float[][] pcmf=_pcmf[0];
	int numbytes = samples * 2 * vi.channels;
	int k = 0;

	buf.ensureSize(numbytes);
	buf.offset = 0;
	buf.length = numbytes;
	result = buf;

	//System.out.println(vi.rate + " " +target+ " " +samples);

        for (int j=0; j<samples; j++){
          for (int i=0; i<vi.channels; i++) {
	     int val = (int) (pcmf[i][_index[i]+j] * 32767.0);
	     if (val > 32767)
	       val = 32767;
	     else if (val < -32768)
	       val = -32768;

             result.data[k++] = (byte) (val >> 8);
             result.data[k++] = (byte) (val % 256);
	  }
        }
        //System.out.println ("decoded "+samples+" samples");
        vd.synthesis_read(samples);
      }
    }
    packet++;

    return result;
  }

  public void stop() {
  }
}
