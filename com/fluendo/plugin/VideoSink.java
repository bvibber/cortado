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
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public class VideoSink extends Sink
{
  private Component component;
  private Frame frame;

  int width, height;
  int aspect_x, aspect_y;

  protected boolean setCaps (Caps caps)
  {
    String mime = caps.getMime();
    if (!mime.equals ("video/raw"))
      return false;

    width = caps.getFieldInt("width", -1);
    height = caps.getFieldInt("height", -1);

    if (width == -1 || height == -1)
      return false;

    aspect_x = caps.getFieldInt("aspect_x", 1);
    aspect_y = caps.getFieldInt("aspect_y", 1);

    if (aspect_y > aspect_x) {
      height = height * aspect_y / aspect_x;
    }
    else {
      width = width * aspect_x / aspect_y;
    }

    frame.setSize (width, height);
    frame.show();

    return true;
  }

  protected int preroll (Buffer buf)
  {
    return render (buf);
  }

  protected int render (Buffer buf)
  {
    Image image;
    
    if (buf.object instanceof ImageProducer) {
      image = frame.createImage((ImageProducer)buf.object);
    }
    else if (buf.object instanceof Image) {
      image = (Image)buf.object;
    }
    else {
      System.out.println(this+": unknown buffer received "+buf);
      return Pad.ERROR;
    }

    if (!frame.isVisible())
      return Pad.NOT_NEGOTIATED;

    Dimension d = frame.getSize();
    Graphics g = frame.getGraphics();
    g.drawImage (image, 0, 0, d.width, d.height, null);

    return Pad.OK;
  };

  public String getName ()
  {
    return "videosink";
  }

  public java.lang.Object getProperty (String name) {
    if (name.equals("component")) {
      return component;
    }
    return null;
  }

  protected int changeState () {
    if (state == STOP && pending == PAUSE) {
      frame = new Frame();
    }
    return super.changeState();
  }
}
