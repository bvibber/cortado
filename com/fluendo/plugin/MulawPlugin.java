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
import com.fluendo.player.*;

public class MulawPlugin extends Plugin 
{
  public MulawPlugin() {
    super(Plugin.TYPE_AUDIO);
  }

  public int typeFind (byte[] data, int offset, int length)
  {
    return Plugin.RANK_NONE;
  }

  public void initDecoder(Component comp) {
    rate = 8000;    
    channels = 1;    
  }

  public byte[] decodeAudio(byte[] data, int offset, int length)
  {
    byte[] bytes = new byte[length];
    System.arraycopy(data, offset, bytes, 0, length);
    return bytes;
  }

  public void stop() {
  }
}
