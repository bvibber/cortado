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

import java.awt.*;
import java.awt.image.*;
import java.util.*;
import com.fluendo.plugin.*;

public class VideoConsumer implements DataConsumer, Runnable
{
  private ImageTarget target;
  private Component component;
  private int queueid;
  private int framenr;
  private Clock clock;
  private boolean ready;
  //private static final int MAX_BUFFER = 1;
  private static final int MAX_BUFFER = 20;
  private double framerate;
  private double frameperiod;
  private double aspect = 1.;
  private boolean stopping = false;
  private double avgratio;
  private Plugin plugin;

  public VideoConsumer(Clock newClock, ImageTarget target, double framerate) {
    this.target = target;

    component = target.getComponent();

    queueid = QueueManager.registerQueue(MAX_BUFFER);
    System.out.println("video on queue "+queueid);
    clock = newClock;
    frameperiod = 1000.0 / framerate;
  }

  public void setPlugin(Plugin pl) {
    plugin = pl;
  }

  public boolean isReady() {
    return ready;
  }

  public void consume(byte[] data, int offset, int length) {
    try {
      byte[] imgData = new byte[length];
      System.arraycopy (data, offset, imgData, 0, length);
      QueueManager.enqueue(queueid, imgData);
    }
    catch (Exception e) { e.printStackTrace();}
  }

  public void stop() {
    stopping = true;
    QueueManager.unRegisterQueue(queueid);
  }

  public void run() {
    System.out.println("entering video thread");
    while (!stopping) {
      //System.out.println("dequeue image");
      byte[] imgData = (byte[]) QueueManager.dequeue(queueid);
      //System.out.println("dequeued image");

      Image image = plugin.decodeVideo (imgData, 0, imgData.length);
      if (plugin.fps_numerator > 0) {
        double framerate = plugin.fps_numerator/(double)plugin.fps_denominator;

        if (framerate != this.framerate) {
          this.framerate = framerate;
          frameperiod = 1000.0 / framerate;
          System.out.println("frameperiod: "+frameperiod);
	}
      }
      aspect = plugin.aspect_numerator/(double)plugin.aspect_denominator;

      try {
	if (framenr == 0) {
	  if (image != null) {
            target.setImage(image, framerate, aspect);
	    // first frame, wait for signal
	    synchronized (clock) {
	      ready = true;
	      System.out.println("video preroll wait");
	      clock.wait();
	      System.out.println("video preroll go!");
	    }
	  }
	}
	else {
	  //System.out.println("wait for "+(framenr * frameperiod));
	  clock.waitForMediaTime((long) (framenr * frameperiod));
	}
      }
      catch (Exception e) {
        e.printStackTrace();
      }
      if (image != null) {
        target.setImage(image, framerate, aspect);
        framenr++;
      }
    }
  }
}
