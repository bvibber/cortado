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

import sun.audio.*;
import java.io.*;

import com.fluendo.utils.*;
import com.fluendo.plugin.*;

public class AudioConsumer implements Runnable, DataConsumer
{
  private int queueid;
  private AudioStream as;
  private boolean ready;
  private Clock clock;
  private static final int MAX_BUFFER = 20;
  private boolean stopping = false;
  private long start;
  private long prev;
  private static final long DEVICE_BUFFER_TIME = 2 * 1024 / 8;
  private Plugin plugin;

  /* muLaw header */
  private static final byte[] header = 
                         { 0x2e, 0x73, 0x6e, 0x64, 		// header in be
                           0x00, 0x00, 0x00, 0x18,              // offset
                           0x7f, 0xff-256, 0xff-256, 0xff-256,  // length
			   0x00, 0x00, 0x00, 0x01,		// ulaw
			   0x00, 0x00, 0x1f, 0x40, 		// frequency
			   0x00, 0x00, 0x00, 0x01		// channels
			 };
  
  class MyIS extends InputStream
  {
    private byte[] current;
    private int pos;
    private int sampleCount;
     
    public MyIS() {
      current = header;
      pos = 0;
    }

    public int available () throws IOException {
      //System.out.println("******** available ");  
      return super.available();
    }

    public int read() {
      int res;

      if (stopping)
        return -1;

      if (current == null) {
        try {
          current = (byte[]) QueueManager.dequeue(queueid);
	}
	catch (InterruptedException ie) {
	  current = null;
	}
	if (current == null)
	  return -1;

	pos = 0;
      }
      res = current[pos];
      if (res < 0) 
        res += 256;

      pos++;

      if (pos >= current.length) {
	current = null;
      }
      if (sampleCount == 0) {
        try {
	  // first sample, wait for signal
	  synchronized (clock) {
	    ready = true;
	    System.out.println("audio preroll wait");
	    clock.wait();
	    System.out.println("audio preroll go!");
	    clock.updateAdjust(-DEVICE_BUFFER_TIME);
	  }
	}
	catch (Exception e) {
	  e.printStackTrace();
	}
      }
      if (sampleCount % 8000 == 7999) {
        long sampleTime = (sampleCount+1)/8;
	long clockTime = clock.getMediaTime();
	long diff = clockTime + DEVICE_BUFFER_TIME + 500 - sampleTime;

	long absDiff = Math.abs(diff);
	long maxDiff = (30 * DEVICE_BUFFER_TIME) / 100;
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
        System.out.println("sync: clock="+clockTime+
	                        " sampleTime="+sampleTime+
	                        " diff="+diff+
			        " adjust="+clock.getAdjust());  
				
	QueueManager.dumpStats();
				*/
      }
      sampleCount++;

      return res;
    }
    public int read(byte[] bytes) throws IOException {
      //System.out.println("******** read "+bytes.length);  
      return super.read(bytes);
    }
    public int read(byte[] bytes, int offset, int len) throws IOException {
      //System.out.println("******** read "+offset+" "+len);  
      int read = super.read(bytes, offset, len);
      return read;
    }
  }

  public AudioConsumer(Clock newClock) {
    queueid = QueueManager.registerQueue(MAX_BUFFER);
    System.out.println("audio on queue "+queueid);
    clock = newClock;
  }

  public boolean isReady() {
    return ready;
  }

  public void stop() {
    stopping = true;
    System.out.println("stopping audio device");
    QueueManager.unRegisterQueue(queueid);
    AudioPlayer.player.stop(as);
    as = null;
  }

  public void run() {
    try {
      System.out.println("entering audio thread");
      start = System.currentTimeMillis();
      as = new AudioStream(new MyIS());
      start = System.currentTimeMillis();
      AudioPlayer.player.start(as);
      System.out.println("exit audio thread");
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setPlugin (Plugin pl) {
    plugin = pl;
  }

  public void consume(byte[] data, int offset, int len) {
    if (plugin == null) 
      return;
     
    byte[] bytes = plugin.decodeAudio (data, offset, len);
    if (bytes != null) {
      try {
        QueueManager.enqueue(queueid, bytes);
      }
      catch (InterruptedException ie) {
      }
    }
  }
}
