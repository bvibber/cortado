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

import java.util.*;

public class MediaBuffer {
  private static Stack pool = new Stack();
  private static int live;

  public Object object;
  public byte[] data;
  public int offset;
  public int length;

  public long time_offset;
  public long timestamp;

  public static MediaBuffer create() {
    MediaBuffer result;
    
    try {
      result = (MediaBuffer) pool.pop();
    }
    catch (EmptyStackException e) {
      result = new MediaBuffer();
      live++;
    }
    result.time_offset = -1;
    result.timestamp = -1;

    return result;
  }

  public MediaBuffer() {
    //System.out.println("new buffer");
  }

  public void free() {
    object = null;
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
