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

import java.io.*;
import java.util.*;
import javax.sound.sampled.*;

import com.fluendo.utils.*;
import com.fluendo.plugin.*;

public class AudioConsumer implements Runnable, DataConsumer, ClockProvider
{
  private int queueid;
  private boolean ready;
  private Clock clock;
  private static final int MAX_BUFFER = 5;
  private boolean stopping = false;
  private Plugin plugin;
  private long queuedTime = -1;
  private Vector preQueue = new Vector();
  private boolean preQueueing = true;
  private long samplesQueued = 0;
  private long sampleCount = 0;
  private long nextSampleCount = 0;
  private SourceDataLine line;

  public AudioConsumer(Clock newClock) {
    queueid = QueueManager.registerQueue(MAX_BUFFER);
    Debug.log(Debug.INFO, "audio on queue "+queueid);
    clock = newClock;

  }

  public boolean isReady() {
    return ready && QueueManager.isFilled (queueid);
  }

  public long getQueuedTime () {
    return queuedTime;
  }

  public void stop() {
    stopping = true;
    QueueManager.unRegisterQueue(queueid);
    line.flush();
    line.stop();
    line.close();
  }

  public void run() {
    try {
      realRun();
    }
    catch (Throwable t) {
      Cortado.shutdown(t);
    }
  }

  public void setPlugin (Plugin pl) {
    plugin = pl;
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

  public long getTime()
  {
    return line.getMicrosecondPosition() / 1000;
  }

  public void checkClockAdjust() 
  {
    if (sampleCount > nextSampleCount) {
      long sampleTime = ((long)line.getFramePosition() * 1000 / plugin.rate) + queuedTime;
      long clockTime = clock.getMediaTime();
      long diff = clockTime - sampleTime;

      long absDiff = Math.abs(diff);
      long maxDiff = 100;
      if (absDiff > maxDiff) {
        long adjust = (long)(Math.log(absDiff - maxDiff) * 20);
        if (diff > 0) {
          clock.updateAdjust(-adjust);
        }
        else if (diff < 0) {
          clock.updateAdjust(adjust);
        }
      }
      /*
      Debug.log(Debug.DEBUG, "sync: clock="+clockTime+
                           " sampleTime="+sampleTime+
                           " diff="+diff+
                           " samples="+sampleCount+
                           " samplediff="+(sampleCount-line.getFramePosition())+
                           " adjust="+clock.getAdjust()+
			   " line="+line.getFramePosition());
			   */

      nextSampleCount = sampleCount + plugin.rate;
    }
  }

  private void handlePrequeue (MediaBuffer buf) 
  {
    boolean have_ts = false;

    samplesQueued += buf.length / (2 * plugin.channels);
    preQueue.addElement (buf);

    //Debug.log(Debug.DEBUG, "audio time: "+buf.timestamp+" "+buf.time_offset+" "+queuedTime+
//	     " "+samplesQueued+" "+ buf.length);
        
    if (buf.time_offset != -1 || buf.time_offset != -1) {
      MediaBuffer headBuf = (MediaBuffer) preQueue.elementAt(0);

      if (buf.timestamp == -1) {
        buf.timestamp = plugin.offsetToTime (buf.time_offset);
      }
      //Debug.log(Debug.DEBUG, "prebuffer head "+headBuf.timestamp);

      headBuf.timestamp = buf.timestamp - (samplesQueued * 1000 / plugin.rate);
	  
      //Debug.log(Debug.DEBUG, "prebuffer head after correction "+headBuf.timestamp);
	    
      AudioFormat format = new AudioFormat(plugin.rate, 16, plugin.channels, true, true);
      DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
      try {
        line = (SourceDataLine) AudioSystem.getLine(info);
        line.open(format);
        Debug.log(Debug.INFO, "line info: available: "+ line.available());
        Debug.log(Debug.INFO, "line info: buffer: "+ line.getBufferSize());
        Debug.log(Debug.INFO, "line info: framePosition: "+ line.getFramePosition());
      }
      catch (Exception e) {
        e.printStackTrace();
      }

      queuedTime = headBuf.timestamp;

      try {
        for (int i=0; i<preQueue.size(); i++) {
          MediaBuffer out = (MediaBuffer) preQueue.elementAt(i);
          //Debug.log(Debug.DEBUG, "writing samples "+ line.available()+" "+out.length);
          line.write (out.data, out.offset, out.length);
	  sampleCount += out.length / (2 * plugin.channels);
	  out.free();
        }
      }
      catch (Exception ie) { 
        ie.printStackTrace();
      }
	    
      preQueue.setSize(0);
      preQueueing = false;
      samplesQueued = 0;

      if (!ready) {
        try {
	  ready = true;
	  // first sample, wait for signal
	  synchronized (clock) {
	    Debug.log(Debug.INFO, "audio preroll wait");
	    clock.wait();
	    Debug.log(Debug.INFO, "audio preroll go!");
	    line.start();
          }
        }
        catch (Exception e) {
          if (!stopping)
            e.printStackTrace();
        }
      }
    }
  }

  public void realRun() {
    Debug.log(Debug.INFO, "entering audio thread");
    while (!stopping) {
      //Debug.log(Debug.DEBUG, "dequeue audio");
      MediaBuffer audioData = null;
      try {
        audioData = (MediaBuffer) QueueManager.dequeue(queueid);
      }
      catch (InterruptedException ie) {
        if (!stopping)
          ie.printStackTrace();
        continue;
      }
      //Debug.log(Debug.DEBUG, "dequeued audio");
    
      MediaBuffer buf = plugin.decode (audioData);
      if (buf != null) {
        if (preQueueing) {
          /* if this is the first sample, try to find the timestamp */
	  handlePrequeue (buf);
        }
        else {
          try {
	    clock.checkPlay ();
	    line.write (buf.data, buf.offset, buf.length);
	    sampleCount += buf.length / (2 * plugin.channels);
	    checkClockAdjust();
          }
          catch (Exception ie) { 
	    if (!stopping)
	      ie.printStackTrace();
	  }
          buf.free();
        }
      }
    }
    Debug.log(Debug.INFO, "exit audio thread");
  }
}
