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

package com.fluendo.jst;

import java.util.*;

public class Buffer {
  private static Stack pool = new Stack();
  private static int live;

  public java.lang.Object object;
  public byte[] data;
  public int offset;
  public int length;
  public Caps caps;

  public long time_offset;
  public long timestamp;

  public static Buffer create() {
    Buffer result;
    
    try {
      result = (Buffer) pool.pop();
    }
    catch (EmptyStackException e) {
      result = new Buffer();
      live++;
    }
    result.time_offset = -1;
    result.timestamp = -1;

    return result;
  }

  public Buffer() {
    //System.out.println("new buffer");
  }

  public void free() {
    object = null;
    caps = null;
    pool.push(this);
  }

  public void ensureSize (int length)
  {
    if (data == null) {
      data = new byte[length];
      //System.out.println("create data "+pool.size()+" "+live+" "+length);
    }
    else if (data.length < length) {
      //System.out.println("expand buffer "+pool.size()+ " "+live+" "+data.length+" -> "+length);
      data = new byte[length];
    }
  }

  public void copyData (byte[] data, int offset, int length)
  {
    ensureSize(length);
    System.arraycopy (data, offset, this.data, 0, length);
    this.offset = 0;
    this.length = length;
  }
}