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

public class JPEGPlugin extends Plugin 
{
  private Component component;
  private MediaTracker mediaTracker;
  private Toolkit toolkit;

  public JPEGPlugin() {
    super(Plugin.TYPE_VIDEO);
  }

  public String getMime ()
  {
    return "image/jpeg";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    return Plugin.RANK_NONE;
  }

  public void initDecoder(Component comp) {
    component = comp;
    mediaTracker = new MediaTracker (comp);
    toolkit = component.getToolkit();

    fps_numerator = -1;
    aspect_numerator = 1;
    aspect_denominator = 1;
  }

  public Image decodeVideo(byte[] data, int offset, int length)
  {
    Image newImage = null;

    newImage = toolkit.createImage(data, offset, length);
    try {
      mediaTracker.addImage(newImage, 0);
      mediaTracker.waitForID(0);
      mediaTracker.removeImage(newImage, 0);
    }
    catch (Exception e) {
      e.printStackTrace();
    }

    return newImage;
  }

  public void stop() {
  }
}
