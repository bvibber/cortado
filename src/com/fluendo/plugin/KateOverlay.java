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
import java.util.*;
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

  /* This class allows lazy rendering, which may not even happen
     if the buffer is late, saving cycles, and ensuring buffers are
     not delayed on their way to the sink */
  private class OverlayProducer implements ImageProducer, ImageConsumer {
    private Vector consumers;

    private Component component;
    private Renderer tr;
    private Buffer buf;
    private java.lang.Object object;

    OverlayProducer(Component c, Renderer tr, Buffer b) {
      consumers = new Vector();
      component = c;
      this.tr = tr;
      this.buf = b;
      object = buf.object;
    }

    public void addConsumer(ImageConsumer ic) {
      if (!isConsumer(ic)) consumers.add(ic);
    }
    public boolean isConsumer(ImageConsumer ic) {
      return consumers.contains(ic);
    }
    public void removeConsumer(ImageConsumer ic) {
      consumers.remove(ic);
      ImageProducer ip = (ImageProducer)object;
      for (int n=0; n<consumers.size(); ++n) {
        ip.removeConsumer(ic);
      }
    }
    public void requestTopDownLeftRightResend(ImageConsumer ic) {
    }
    public void startProduction(ImageConsumer ic) {
      Image img = null;

      addConsumer(ic);

      if (image_dimension == null) {
        img = getImage(object);
        if (img == null) {
          sendError();
          return;
        }
        image_dimension = new Dimension(img.getWidth(null), img.getHeight(null));
      }

      /* before rendering, we update the state of the events; for now this
         just weeds out old ones, but at some point motions could be tracked. */
      int ret = tr.update(component, image_dimension, buf.timestamp/(double)Clock.SECOND);

      if (ret < 0) {
        Debug.log(Debug.WARNING, "Failed to update jtiger renderer");
        sendOriginalImage();
        return;
      }

      /* if the renderer is empty and the buffer is not a duplicate, we leave the
         video alone */
      if (!buf.duplicate && ret > 0) {
        Debug.log(Debug.DEBUG, "Video frame is not a dupe and we have nothing to overlay.");
        sendOriginalImage();
        return;
      }

      /* if the renderer isn't dirty and the image hasn't changed, we don't need
         to do anything, as the result image would be the same */
      if (buf.duplicate && !tr.isDirty()) {
        Debug.log(Debug.DEBUG, "Video frame is a dupe and we're not dirty. Yeah.");
        sendOriginalImage();
        return;
      }

      /* render Kate stream on top */
      if (img == null) {
        img = getImage(object);
      }
      img = tr.render(component, img);

      /* We need to draw a new overlay, so we need to get the buffer to update,
         as it might have a previous overlay on top of it */
      buf.duplicate = false;

      sendImage(img);
    }

    private Image getImage(java.lang.Object object) {
      Image img;
      if (object instanceof ImageProducer) {
        img = component.createImage((ImageProducer)object);
      }
      else if (object instanceof Image) {
        img = (Image)object;
      }
      else {
        System.out.println(this+": unknown buffer received "+object);
        img = null;
      }
      return img;
    }

    /* tells the consumers there was an error producing the image */
    private void sendError() {
      Debug.log(Debug.WARNING, "Sending image error notification");
      for (int n=0; n<consumers.size(); ++n) {
        ImageConsumer ic = (ImageConsumer)consumers.elementAt(n);
        ic.imageComplete(ImageConsumer.IMAGEERROR);
      }
    }

    /* sends the original image, unmodified, to the consumers, by forwarding all
       ImageConsumer calls from the original image to our own consumers */
    private void sendOriginalImage() {
      ImageProducer ip = (ImageProducer)object;
      ip.startProduction(this);
    }

    /* sends the given image to the consumers */
    private void sendImage(Image img) {
      PixelGrabber pg = new PixelGrabber(img, 0, 0, -1, -1, false);
      try {
        if (pg.grabPixels(0)) {
          int[] pixels = (int[])pg.getPixels();
          if (pixels == null) {
            Debug.log(Debug.WARNING, "pixels are null!");
            sendError();
          }
          else {
            for (int n=0; n<consumers.size(); ++n) {
              ImageConsumer ic = (ImageConsumer)consumers.elementAt(n);
              ic.setHints(ImageConsumer.TOPDOWNLEFTRIGHT |
                          ImageConsumer.COMPLETESCANLINES |
                          ImageConsumer.SINGLEFRAME |
                          ImageConsumer.SINGLEPASS);
              ic.setDimensions(image_dimension.width, image_dimension.height);
              ic.setPixels(0, 0, image_dimension.width, image_dimension.height, pg.getColorModel(), pixels, 0, image_dimension.width);
              ic.imageComplete(ImageConsumer.STATICIMAGEDONE);
            }
          }
        }
        else {
          Debug.log(Debug.WARNING, "Failed to grab pixels");
          sendError();
        }
      }
      catch (Exception e) {
        Debug.log(Debug.WARNING, "Failed to grab pixels: "+e.toString());
        sendError();
      }
    }

    /* ImageConsumer interface, to redirect calls from the original image */

    public void imageComplete(int status) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).imageComplete(status);
    }
    public void setColorModel(ColorModel cm) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).setColorModel(cm);
    }
    public void setDimensions(int w, int h) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).setDimensions(w, h);
    }
    public void setHints(int hints) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).setHints(hints);
    }
    public void setProperties(java.util.Hashtable props) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).setProperties(props);
    }
    public void setPixels(int x, int y, int w, int h, ColorModel model, byte[] pixels, int off, int scansize) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).setPixels(x, y, w, h, model, pixels, off, scansize);
    }
    public void setPixels(int x, int y, int w, int h, ColorModel model, int[] pixels, int off, int scansize) {
      for (int n=0; n<consumers.size(); ++n) ((ImageConsumer)consumers.elementAt(n)).setPixels(x, y, w, h, model, pixels, off, scansize);
    }

  };

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

  /**
   * Overlay the Kate renderer onto the given image.
   */
  protected synchronized void overlay(com.fluendo.jst.Buffer buf) {
    buf.object = new OverlayProducer(component, tr, buf);
  }

  public String getFactoryName ()
  {
    return "kateoverlay";
  }
}
