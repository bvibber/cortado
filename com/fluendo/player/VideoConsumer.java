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
  private long framenr;
  private Clock clock;
  private boolean ready;
  private static final int MAX_BUFFER = 100;
  private double framerate;
  private double frameperiod;
  private double aspect = 1.;
  private boolean stopping = false;
  private Plugin plugin;
  private long queuedTime = -1;
  private Vector preQueue = new Vector();
  private boolean preQueueing = true;
  private long framesQueued = 0;

  public VideoConsumer(Clock newClock, ImageTarget target, double framerate) {
    this.target = target;

    component = target.getComponent();

    queueid = QueueManager.registerQueue(MAX_BUFFER);
    System.out.println("video on queue "+queueid);
    clock = newClock;
    if (framerate > 0) {
      frameperiod = 1000.0 / framerate;
    }
    else {
      frameperiod = -1;
    }
  }

  public void setPlugin(Plugin pl) {
    plugin = pl;
  }

  public boolean isReady() {
    return ready;
  }

  public void consume(MediaBuffer buffer) {
    try {
      QueueManager.enqueue(queueid, buffer);
    }
    catch (Exception e) { 
      if (!stopping)
        e.printStackTrace();
    }
  }

  public long getQueuedTime () {
    return queuedTime;
  }

  public void stop() {
    stopping = true;
    QueueManager.unRegisterQueue(queueid);
  }

  public void run() {
    try {
      realRun();
    }
    catch (Throwable t) {
      Cortado.shutdown(t);
    }
  }
  private void handleDisplay (MediaBuffer buf)
  {
    ImageProducer imageProd = (ImageProducer) buf.object;
    try {
      if (frameperiod > 0) {
        long timestamp = buf.timestamp;
        if (timestamp == -1) {
          timestamp = (long) (framenr * frameperiod);
        }
        if (clock.waitForMediaTime((long) (timestamp))) {
	  //System.out.println("set image "+timestamp);
          target.setImageProducer(imageProd, framerate, aspect);
        }
	else {
	  //System.out.println("skip image "+timestamp);
	}
      }
      else {
        //System.out.println("set image");
        target.setImageProducer(imageProd, framerate, aspect);
      }
    }
    catch (Exception ie) {
      if (!stopping)
        ie.printStackTrace();
    }
    framenr++;
    buf.free();
  }

  private void handlePrequeue (MediaBuffer buf) 
  {
    boolean have_ts = false;

    //System.out.println("video time: "+buf.timestamp+" "+buf.time_offset+" "+queuedTime+
//	     " "+framesQueued+" "+ buf.length);

    preQueue.addElement (buf);
        
    if (buf.timestamp != -1 || buf.time_offset != -1) {
      MediaBuffer headBuf = (MediaBuffer) preQueue.elementAt(0);

      if (buf.timestamp == -1) {
        buf.timestamp = plugin.offsetToTime (buf.time_offset);
      }
      //System.out.println("prebuffer head "+headBuf.timestamp);

      headBuf.timestamp = buf.timestamp - (long)(framesQueued * 1000 / framerate);
      framenr = (long) (headBuf.timestamp / frameperiod);
	  
      //System.out.println("prebuffer head after correction "+headBuf.timestamp);

      if (!ready) {
        try {
	  ImageProducer prod = (ImageProducer)headBuf.object;
          target.setImageProducer(prod, framerate, aspect);
	  queuedTime = headBuf.timestamp;
	  headBuf.free();
	  // first frame, wait for signal
	  synchronized (clock) {
	    ready = true;
	    System.out.println("video preroll wait");
	    clock.wait();
	    System.out.println("video preroll go!");
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
	framenr++;
      }
	    
      for (int i=1; i<preQueue.size(); i++) {
        MediaBuffer out = (MediaBuffer) preQueue.elementAt(i);
	handleDisplay (out);
      }
      preQueue.setSize(0);
      preQueueing = false;
      framesQueued = 0;
    }
    else {
      //System.out.println("video queueing");
      framesQueued++;
    }
  }

  private void realRun() {
    System.out.println("entering video thread");
    while (!stopping) {
      //System.out.println("dequeue image");
      MediaBuffer imgData = null;
      try {
        imgData = (MediaBuffer) QueueManager.dequeue(queueid);
      }
      catch (InterruptedException ie) {
        if (!stopping)
	  ie.printStackTrace();
	continue;
      }
      //System.out.println("dequeued image");

      MediaBuffer buf = plugin.decode (imgData);
      if (buf != null) {
        //System.out.println("decoded image");
        if (plugin.fps_numerator > 0) {
          double fps = plugin.fps_numerator/(double)plugin.fps_denominator;

          if (fps != framerate) {
            framerate = fps;
            frameperiod = 1000.0 / fps;
            System.out.println("frameperiod: "+frameperiod);
	  }
        }
	else {
	  if (preQueueing)
	    buf.timestamp = 0;
	}
        aspect = plugin.aspect_numerator/(double)plugin.aspect_denominator;

	if (preQueueing) {
	  handlePrequeue (buf);
	}
	else {
	  handleDisplay (buf);
	}
      }
    }
    System.out.println("exit video thread");
  }
}
