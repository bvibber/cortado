/* Copyright (C) <2008> ogg.k.ogg.k <ogg.k.ogg.k@googlemail.com>
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
import com.fluendo.jtiger.Renderer;
import com.fluendo.utils.*;

/* This element renders a Kate stream on incoming video */
public class KateOverlay extends Overlay
{
  private Font font = null;
  private String text = null;
  private Renderer tr = new Renderer();
  private Dimension image_dimension = null;

  private Pad kateSinkPad = new Pad(Pad.SINK, "katesink") {
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      /* don't propagate, the video sink is the master */

      switch (event.getType()) {
        case com.fluendo.jst.Event.FLUSH_START:
        case com.fluendo.jst.Event.FLUSH_STOP:
        case com.fluendo.jst.Event.NEWSEGMENT:
          onFlush();
          break;
	default:
          break;
      }
      return true;
    }

    /**
     * This pad receives Kate events, and add them to the renderer.
     * They will be removed from it as they become inactive.
     */
    protected synchronized int chainFunc (com.fluendo.jst.Buffer buf) {
      addKateEvent((com.fluendo.jkate.Event)buf.object);
      return Pad.OK;
    }
  };

  /**
   * Create a new Kate overlay
   */
  public KateOverlay() {
    super();
    addPad(kateSinkPad);
  }


  /**
   * Add a new Kate event to the renderer.
   * This needs locking so the Kate events are not changed while the
   * overlay is rendering them to an image.
   */
  protected synchronized void addKateEvent(com.fluendo.jkate.Event ev) {
    tr.add(ev);
    Debug.log(Debug.DEBUG, "Kate overlay got Kate event: "+new String(ev.text));
  }

  /**
   * Upon a flushing event, remove any existing event, now obsolete.
   * This needs locking so the Kate events are not changed while the
   * overlay is rendering them to an image.
   */
  protected synchronized void onFlush() {
    tr.flush();
    image_dimension = null;
    Debug.log(Debug.DEBUG, "Kate overlay flushing");
  }

  protected Image getImage(com.fluendo.jst.Buffer buf) {
    Image img;
    if (buf.object instanceof ImageProducer) {
      img = component.createImage((ImageProducer)buf.object);
    }
    else if (buf.object instanceof Image) {
      img = (Image)buf.object;
    }
    else {
      System.out.println(this+": unknown buffer received "+buf);
      img = null;
    }
    return img;
  }

  /**
   * Overlay the Kate renderer onto the given image.
   */
  protected synchronized void overlay(com.fluendo.jst.Buffer buf) {
    Image img = null;

    if (image_dimension == null) {
      img = getImage(buf);
      if (img == null) {
        return;
      }
      image_dimension = new Dimension(img.getWidth(null), img.getHeight(null));
    }

    /* before rendering, we update the state of the events; for now this
       just weeds out old ones, but at some point motions could be tracked. */
    int ret = tr.update(component, image_dimension, buf.timestamp/(double)Clock.SECOND);
    if (ret < 0) {
      Debug.log(Debug.WARNING, "Failed to update jtiger renderer");
      return;
    }

    /* if the renderer isn't dirty and the image hasn't changed, we don't need
       to do anything, as the result image would be the same */
    if (buf.duplicate && !tr.isDirty()) {
      Debug.log(Debug.DEBUG, "Video frame is a dupe and we're not dirty. Yeah.");
      return;
    }

    /* render Kate stream on top */
    if (img == null) {
      img = getImage(buf);
    }
    img = tr.render(component, img);

    /* We need to draw a new overlay, so we need to get the buffer to update,
       as it might have a previous overlay on top of it */
    buf.duplicate = false;

    buf.object = img;
  }

  public String getFactoryName ()
  {
    return "kateoverlay";
  }
}
