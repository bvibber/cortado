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
import com.jcraft.jogg.*;
import com.fluendo.jheora.*;
import com.fluendo.player.*;

public class TheoraPlugin extends Plugin 
{
  private Component component;
  private Toolkit toolkit;

  private Info ti;
  private Comment tc;
  private State ts;
  private Packet op;
  private int packet;
  private YUVBuffer yuv;

  public TheoraPlugin() {
    super(Plugin.TYPE_VIDEO);
  }

  public int typeFind (byte[] data, int offset, int length)
  {
    if (data[offset+1] == 0x74) {
      return Plugin.RANK_PRIMARY;
    }
    return Plugin.RANK_NONE;
  }

  public void initDecoder(Component comp) {
    component = comp;
    toolkit = component.getToolkit();

    ti = new Info();
    tc = new Comment();
    ts = new State();
    yuv = new YUVBuffer();
    op = new Packet();
    packet = 0;
  }

  public Image decodeVideo(byte[] data, int offset, int length)
  {
    Image newImage =  null;

    op.packet_base = data;
    op.packet = offset;
    op.bytes = length;
    op.b_o_s = (packet == 0 ? 1 : 0);
    op.e_o_s = 0;
    op.packetno = packet;
    
    if (packet < 3) {
      //System.out.println ("decoding header");
      if (ti.decodeHeader(tc, op) < 0){
        // error case; not a theora header
        System.err.println("does not contain Theora video data.");
        return null;
      }
      if (packet == 2) {
        ts.decodeInit(ti);
    
        System.out.println("theora dimension: "+ti.width+"x"+ti.height);
        if (ti.aspect_denominator == 0) {
          ti.aspect_numerator = 1;
          ti.aspect_denominator = 1;
        }
        System.out.println("theora offset: "+ti.offset_x+","+ti.offset_y);
        System.out.println("theora frame: "+ti.frame_width+","+ti.frame_height);
        System.out.println("theora aspect: "+ti.aspect_numerator+"/"+ti.aspect_denominator);
        System.out.println("theora framerate: "+ti.fps_numerator+"/"+ti.fps_denominator);

        fps_numerator = ti.fps_numerator;
        fps_denominator = ti.fps_denominator;
        aspect_numerator = ti.aspect_numerator;
        aspect_denominator = ti.aspect_denominator;
      }
    }
    else {
      if (ts.decodePacketin(op) != 0) {
        System.err.println("Error Decoding Theora.");
        return null;
      }
      if (ts.decodeYUVout(yuv) != 0) {
        System.err.println("Error getting the picture.");
        return null;
      }
      newImage = yuv.getAsImage(toolkit, ti.offset_x, ti.offset_y, ti.frame_width, ti.frame_height);
    }
    packet++;

    return newImage;
  }

  public void stop() {
  }
}
