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

  public byte[] decodeAudio(byte[] data, int offset, int length)
  {
    byte[] bytes = null;

    //System.out.println ("creating packet");
    op.packet_base = data;
    op.packet = offset;
    op.bytes = length;
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
  	int target = 8000 * samples / vi.rate;
        bytes = new byte[target];

	//System.out.println(vi.rate + " " +target+ " " +samples);

        for (int j=0; j<target; j++){
	  float val = 0.0f;

          for (int i=0; i<vi.channels; i++) {
	    val += pcmf[i][_index[i]+(vi.rate * j / 8000)];
	  }
	  val /= vi.channels;

          int sample = (int) (val * 32768);
	  int sign, exponent, mantissa, ulawbyte;

   	  if (sample>32767) sample=32767;
	  else if (sample<-32768) sample=-32768;
	  /* Get the sample into sign-magnitude. */
          sign = (sample >> 8) & 0x80;    /* set aside the sign */
	  if (sign != 0) sample = -sample;    /* get magnitude */
          if (sample > CLIP) sample = CLIP;    /* clip the magnitude */

          /* Convert from 16 bit linear to ulaw. */
          sample = sample + BIAS;
          exponent = exp_lut[(sample >> 7) & 0xFF];
          mantissa = (sample >> (exponent + 3)) & 0x0F;
          ulawbyte = ~(sign | (exponent << 4) | mantissa);
          if (ZEROTRAP)
            if (ulawbyte == 0) ulawbyte = 0x02;  /* optional CCITT trap */

	  bytes[j] = (byte)ulawbyte;
        }
        //System.out.println ("decoded "+samples+" samples");
        vd.synthesis_read(samples);
      }
    }
    packet++;

    return bytes;
  }

  public void stop() {
  }
}
