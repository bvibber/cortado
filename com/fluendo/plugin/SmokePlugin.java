/* Smoke Codec
 * Copyright (C) <2004> Wim Taymans <wim@fluendo.com>
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
import com.fluendo.codecs.*;
import com.fluendo.player.*;

public class SmokePlugin extends Plugin 
{
  private Component component;
  private MediaTracker mediaTracker;
  private SmokeCodec smoke;

  public SmokePlugin() {
    super(Plugin.TYPE_VIDEO);
  }

  public int typeFind (byte[] data, int offset, int length)
  {
    if (data[offset+1] == 0x73) {
      return Plugin.RANK_PRIMARY;
    }
    return Plugin.RANK_NONE;
  }

  public void initDecoder(Component comp) {
    component = comp;
    mediaTracker = new MediaTracker (comp);
    smoke = new SmokeCodec (component, mediaTracker);
  }

  public Image decodeVideo(byte[] data, int offset, int length)
  {
    Image newImage;

    newImage = smoke.decode(data, offset, length);
    
    fps_numerator = smoke.fps_num;
    fps_denominator = smoke.fps_denom;
    aspect_numerator = 1;
    aspect_denominator = 1;

    return newImage;
  }

  public void stop() {
  }
}
