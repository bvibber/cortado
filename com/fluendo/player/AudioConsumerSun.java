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
import sun.audio.*;

import com.fluendo.utils.*;
import com.fluendo.plugin.*;

public class AudioConsumerSun implements Runnable, DataConsumer, ClockProvider
{
  private int queueid;
  private boolean ready;
  private Clock clock;
  private static final int MAX_BUFFER = 100;
  private boolean stopping = false;
  private Plugin plugin;
  private long queuedTime = -1;
  private Vector preQueue = new Vector();
  private boolean preQueueing = true;
  private long samplesQueued = 0;
  private long sampleCount = 0;
  private long nextSampleCount = 0;
  private AudioBuffer audioBuffer;
  private AudioStream audioStream;
  private static final long DEVICE_BUFFER = 8 * 1024;

  private static final boolean ZEROTRAP=true;
  private static final short BIAS=0x84;
  private static final int CLIP=32635;
  private static final byte[] exp_lut =
    { 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3, 3,
      4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
      7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };

  /* muLaw header */
  private static final int[] header = 
                         { 0x2e, 0x73, 0x6e, 0x64, 		// header in be
                           0x00, 0x00, 0x00, 0x18,              // offset
                           0x7f, 0xff, 0xff, 0xff,  		// length
			   0x00, 0x00, 0x00, 0x01,		// ulaw
			   0x00, 0x00, 0x1f, 0x40, 		// frequency
			   0x00, 0x00, 0x00, 0x01		// channels
			 };
  
  class AudioBuffer extends InputStream
  {
    private int readPtr;
    private int writePtr;
    private int rate;
    private int channels;
    private byte[] buffer;
    private boolean started;
    private boolean needHeader = true;
    private long bufstart;
    private long bufend;
    private int resampleWrap;
    private long samplesRead;
    private long samplesWritten;
    private long free;
     
    public AudioBuffer(int size, int rate, int channels) {
      readPtr = 0;
      writePtr = 0;
      this.rate = rate;
      this.channels = channels;
      buffer = new byte[size];
      for (int i=0; i<buffer.length;i++) {
        buffer[i] = 0x7f;
      }
      started = false;
      bufstart = 0;
      bufend = size;
      free = size;
      try {
        audioStream = new AudioStream (this);
        AudioPlayer.player.start(audioStream);
	Thread.currentThread().sleep(1000);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    public int available () throws IOException {
      //System.out.println("******** available ");  
      return super.available();
    }

    public long getFramePosition() {
      return samplesRead - DEVICE_BUFFER;
    }

    public void start()
    {
      started = true;
    }

    private final byte toUlaw(int sample)
    {
    /*
      */
      int sign, exponent, mantissa, ulawbyte;

      if (sample>32767) sample=32767;
      else if (sample<-32768) sample=-32768;
      /* Get the sample into sign-magnitude. */
      sign = (sample >> 8) & 0x80;    /* set aside the sign */
      if (sign != 0) sample = -sample;    /* get magnitude */
      if (sample > CLIP) sample = CLIP;    /* clip the magnitude */
       
      /* Convert from 16 bit linear to ulaw. */
      sample = sample + BIAS;
      exponent = exp_lut[(sample >> 7) & 0xFF];
      mantissa = (sample >> (exponent + 3)) & 0x0F;
      ulawbyte = ~(sign | (exponent << 4) | mantissa);
      if (ZEROTRAP)
        if (ulawbyte == 0) ulawbyte = 0x02;  /* optional CCITT trap */

      return (byte) ulawbyte;
    }

    public int read() {
      int res;

      if (stopping)
        return -1;
      
      if (needHeader) {
        res = header[readPtr];
	readPtr++;
	if (readPtr >= header.length) {
	  readPtr = 0;
	  needHeader = false;
	}
      }
      else {
        if (!started) {
          res = 0x7f;
	}
	else {
	  res = buffer[readPtr];
	  if (res < 0)
	    res += 256;

	  buffer[readPtr++] = 0x7f;
	  free++;
	  if (free >= buffer.length) {
	    /* underrun !! */
	    free = buffer.length-1;
	    writePtr++;
	    notify();
	  }

	  if (readPtr >= buffer.length) {
	    readPtr = 0;
	    bufstart = bufend;
	    bufend = bufstart + buffer.length;
          }
	  samplesRead++;
	}
      }
      //System.out.println("******** read "+res);
      return res;
    }
    public synchronized int read(byte[] bytes) throws IOException {
      //System.out.println("******** read "+bytes.length);  
      if (started)
        checkClockAdjust();
      int read = super.read(bytes);
      notify();
      return read;
    }
    public synchronized int read(byte[] bytes, int offset, int len) throws IOException {
      //System.out.println("******** read "+offset+" "+len);  
      if (started)
        checkClockAdjust();
      int read = super.read(bytes, offset, len);
      notify();
      return read;
    }

    public synchronized int write(byte[] bytes, int offset, int len) throws IOException 
    {
      int ptr;
      int inc = 0;
      int samples = len / (2 * channels);

      while (free < samples) {
        try {
          wait();
	}
	catch (Exception e) {}
      }
      ptr = resampleWrap;
      
      while (ptr < len) {
        int sample = 0;
        for (int j=0; j<channels; j++) {
	  int b1, b2;

	  b1 = bytes[offset+ptr + 2*j];
	  b2 = bytes[offset+ptr+1 + 2*j];
	  if (b2<0) b2+=256;

	  sample += (b1 * 256) | b2;
        }
        sample /= channels;

        buffer[writePtr] = toUlaw (sample);
        if (++writePtr >= buffer.length)
	  writePtr = 0;
	free--;

	if (readPtr == writePtr) {
	  try {
	    wait();
	  }
	  catch (Exception e) {}
	}
        inc++;

	ptr = 2 * channels * (rate * inc / 8000);
      }
      resampleWrap = ptr - len;
      
      return len;
    }
  }

  public AudioConsumerSun(Clock newClock) {
    queueid = QueueManager.registerQueue(MAX_BUFFER);
    System.out.println("audio on queue "+queueid);
    clock = newClock;
  }

  public boolean isReady() {
    return ready;
  }

  public long getQueuedTime () {
    return queuedTime - DEVICE_BUFFER/8;
  }

  public void stop() {
    stopping = true;
    QueueManager.unRegisterQueue(queueid);
    AudioPlayer.player.stop(audioStream);
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
    return 0; //line.getMicrosecondPosition() / 1000;
  }

  public void checkClockAdjust() 
  {
    if (audioBuffer == null)
      return;
    long sampleTime = ((long)audioBuffer.getFramePosition() * 1000 / 8000) + queuedTime;
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
    System.out.println("sync: clock="+clockTime+
                           " sampleTime="+sampleTime+
                           " diff="+diff+
                           " samples="+sampleCount+
                           " samplediff="+(sampleCount-audioBuffer.getFramePosition())+
                           " adjust="+clock.getAdjust()+
			   " line="+audioBuffer.getFramePosition());
			   */
  }

  private void handlePrequeue (MediaBuffer buf) 
  {
    boolean have_ts = false;

    samplesQueued += buf.length / (2 * plugin.channels);
    preQueue.addElement (buf);

    //System.out.println("audio time: "+buf.timestamp+" "+buf.time_offset+" "+queuedTime+
//	     " "+samplesQueued+" "+ buf.length);
        
    if (buf.time_offset != -1 || buf.time_offset != -1) {
      MediaBuffer headBuf = (MediaBuffer) preQueue.elementAt(0);

      if (buf.timestamp == -1) {
        buf.timestamp = plugin.offsetToTime (buf.time_offset);
      }
      //System.out.println("prebuffer head "+headBuf.timestamp);

      headBuf.timestamp = buf.timestamp - (samplesQueued * 1000 / plugin.rate);
	  
      //System.out.println("prebuffer head after correction "+headBuf.timestamp);
	    
      audioBuffer = new AudioBuffer(100000, plugin.rate, plugin.channels);

      queuedTime = headBuf.timestamp;

      try {
        for (int i=0; i<preQueue.size(); i++) {
          MediaBuffer out = (MediaBuffer) preQueue.elementAt(i);
          //System.out.println("writing samples "+ line.available()+" "+out.length);
          audioBuffer.write (out.data, out.offset, out.length);
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
	  // first sample, wait for signal
	  synchronized (clock) {
	    ready = true;
	    System.out.println("audio preroll wait");
	    clock.wait();
	    System.out.println("audio preroll go!");
	    audioBuffer.start();
          }
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  public void realRun() {
    System.out.println("entering audio thread");
    while (!stopping) {
      //System.out.println("dequeue audio");
      MediaBuffer audioData = null;
      try {
        audioData = (MediaBuffer) QueueManager.dequeue(queueid);
      }
      catch (InterruptedException ie) {
        if (!stopping)
          ie.printStackTrace();
        continue;
      }
      //System.out.println("dequeued audio");
    
      MediaBuffer buf = plugin.decode (audioData);
      if (buf != null) {
        if (preQueueing) {
          /* if this is the first sample, try to find the timestamp */
	  handlePrequeue (buf);
        }
        else {
          try {
	    clock.checkPlay ();
	    audioBuffer.write (buf.data, buf.offset, buf.length);
	    sampleCount += buf.length / (2 * plugin.channels);
          }
          catch (Exception ie) { 
	    ie.printStackTrace();
	  }
          buf.free();
        }
      }
    }
    System.out.println("exit audio thread");
  }
}
