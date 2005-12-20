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
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public class JPEGDec extends Element 
{
  private Toolkit toolkit;
  private int width, height;

  private Pad srcpad = new Pad(Pad.SRC, "src") {
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      return sinkpad.pushEvent(event);
    }
  };

  private Pad sinkpad = new Pad(Pad.SINK, "sink") {
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      boolean result;

      switch (event.getType()) {
        case com.fluendo.jst.Event.FLUSH_START:
          result = srcpad.pushEvent (event);
          synchronized (streamLock) {
            Debug.log(Debug.INFO, "synced "+this);
          }
          break;
        case com.fluendo.jst.Event.FLUSH_STOP:
          synchronized (streamLock) {
            result = srcpad.pushEvent(event);
          }
          break;
        case com.fluendo.jst.Event.EOS:
        case com.fluendo.jst.Event.NEWSEGMENT:
        default:
          synchronized (streamLock) {
            result = srcpad.pushEvent(event);
          }
          break;
      }
      return result;
    }
    protected int chainFunc (com.fluendo.jst.Buffer buf) {
      int ret;
      Image img = null; 

      img = toolkit.createImage(buf.data, buf.offset, buf.length);
      if (img != null) {
        if (img.getWidth(null) != width || img.getHeight(null) != height) {
	  width = img.getWidth(null);
	  height = img.getHeight(null);

          Debug.log(Debug.INFO, "jpeg frame: "+width+","+height);

          caps = new Caps ("video/raw");
          caps.setFieldInt ("width", width);
          caps.setFieldInt ("height", height);
          caps.setFieldInt ("aspect_x", 1);
          caps.setFieldInt ("aspect_y", 1);
	}
        buf.object = img;
        buf.caps = caps;

        ret = srcpad.push(buf);
      }
      else {
	Debug.log (Debug.WARNING, "could not decode jpeg image");
        buf.free();
	ret = OK;
      }
      return ret;
    }
  };

  public JPEGDec() {
    super();

    toolkit = Toolkit.getDefaultToolkit();

    addPad (srcpad);
    addPad (sinkpad);
  }

  protected int changeState (int transition) {
    int res;

    switch (transition) {
      case STOP_PAUSE:
        width = -1;
        height = -1;
        break;
      default:
        break;
    }

    res = super.changeState (transition);

    return res;
  }

  public String getName ()
  {
    return "jpegdec";
  }
  public String getMime ()
  {
    return "image/jpeg";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    return -1;
  }

}
