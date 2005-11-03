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

import java.util.*;
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public abstract class AudioSink extends Sink implements ClockProvider
{
  public static final int SEGSIZE = 8192;

  private RingBuffer ringBuffer = null;

  private Clock clock = new SystemClock() {
    private long lastTime = -1;
    private long diff = -1;
    private long base;

    protected synchronized long getInternalTime() {
      long samples;
      long result;
      long timePos;
      
      if (ringBuffer == null || ringBuffer.rate == 0)
        return 0;
      
      samples = ringBuffer.samplesPlayed();
      timePos = samples * Clock.SECOND / ringBuffer.rate;

      /* interpolate as the position can jump a lot */
      long now = System.currentTimeMillis() * Clock.MSECOND;
      if (diff == -1) {
        diff = now;
	base = now;
      }

      if (timePos != lastTime) {
        lastTime = timePos;
        diff = now - timePos;
      }
      result = now - diff;

      //System.out.println("time: "+result+", now: "+now+", diff: "+diff+", timePos: "+timePos);

      return result;
    }
  };

  public Clock provideClock() {
    return clock;
  }

  protected class RingBuffer implements Runnable {
    private byte[] buffer;
    private int state;
    private Thread thread;
    private long nextSample;
    private int bps, sps;

    private static final int STOP = 0;
    private static final int PAUSE = 1;
    private static final int PLAY = 2;

    public byte[] emptySeg;
    public long playSeg;
    public int segTotal;
    public int segSize;
    public int rate, channels;

    public void run() {
      while (true) {
	synchronized (this) {
	  if (state != PLAY) {
	    while (state == PAUSE) {
	      try {
                notifyAll();
	        wait();
	      }
	      catch (InterruptedException ie) {}
	    }
	    if (state == STOP) {
	      break;
	    }
	  }
	}
        
        int segNum = (int) (playSeg % segTotal);
        int index = segNum * segSize; 
	int ret, toWrite;

        toWrite = segSize;
        while (toWrite > 0) {
	  ret = write (buffer, index, segSize);

	  toWrite -= ret;
	}

        clear (segNum);

	synchronized (this) {
	  playSeg++;
	  notifyAll();
	}
      }
    }

    public synchronized boolean acquire(Caps caps) {
      boolean res;

      if (thread != null)
        return false;

      String mime = caps.getMime();
      if (!mime.equals ("audio/raw"))
        return false;

      rate = caps.getFieldInt("rate", 44100);
      channels = caps.getFieldInt("channels", 1);

      if ((res = open (this)) == false)
        return res;

      buffer = new byte[segSize * segTotal];
      bps = 2 * channels;
      sps = segSize / bps;

      state = PAUSE;
      nextSample = 0;
      playSeg = 0;

      thread = new Thread(this);
      thread.start();
      try {
        wait();
      }
      catch (InterruptedException ie) {}

      return res;
    }
    public synchronized boolean release() {
      boolean res;

      stop();

      if (thread == null)
        return true;

      res = close(this);

      return res;
    }

    private synchronized boolean waitSegment() {
      if (state != PLAY)
        play();

      try {
	if (state != PLAY)
	  return false;
	    
        wait();

	if (state != PLAY)
	  return false;
      }
      catch (InterruptedException ie) {}

      return true;
    }

    public int commit (byte[] data, long sample, int offset, int len) {
      int idx;

      if (sample == -1) {
        sample = nextSample;
      }
      else if (sample < 0) {
        return len;
      }
      else if (sample != nextSample) {
        Debug.log(Debug.WARNING, "discont found, expected "+nextSample+", got "+sample);
	setSample (sample);
      }

      idx = 0;

      nextSample = sample + len / bps;
      while (len > 0) {
        long writeSeg;
	int writeOff;
	int writeLen = 0;
	long diff = -1;

	writeSeg = sample / sps;
	writeOff = (int) ((sample % sps) * bps);

	while (true) {
	  /* get the currently playing segment */
	  synchronized (this) {
	    /* see how far away it is from the write segment */
	    diff = writeSeg - playSeg;
	  }

          /* play segment too far ahead, we need to drop */
          if (diff < 0) {
            /* we need to drop one segment at a time, pretend we wrote a
             * segment. */
            writeLen = Math.min (segSize, len);
            break;
          }
	  else {
            /* write segment is within writable range, we can break the loop and
             * start writing the data. */
            if (diff < segTotal)
              break;

            /* else we need to wait for the segment to become writable. */
            if (!waitSegment ()) {
              return -1;
	    }
	  }
        }
	if (diff >= 0) {
	  int writeSegRel;

          /* we can write now */
          writeSegRel = (int) (writeSeg % segTotal);
	  writeLen = Math.min (segSize - writeOff, len);

          System.arraycopy (data, idx, buffer, writeSegRel * segSize + writeOff, writeLen);
	}

        len -= writeLen;
        idx += writeLen;
        sample += writeLen / bps;
      }

      return len;
    }

    public synchronized long samplesPlayed () {
      long delay, samples;
      long seg;

      /* get the number of samples not yet played */
      delay = delay ();
      
      seg = Math.max (0, playSeg - 1); 

      samples = (seg * sps);

      if (samples >= delay)
        samples -= delay;

      return samples;
    }
    public synchronized void clear (long segNum) 
    {
      int index = ((int)(segNum % segTotal)) * segSize;

      System.arraycopy (emptySeg, 0, buffer, index, segSize);
    }
    public synchronized void setSample (long sample) {

      if (sample == -1)
        sample = 0;

      playSeg = sample / sps;
      nextSample = sample;

      for (int i = 0; i < segTotal; i++) {
        clear (i);
      }
      synchronized (clock) {
        clock.notifyAll();
      }
    }

    public synchronized boolean play () {
      state = PLAY;
      notifyAll();
      return true;
    }
    public synchronized boolean pause () {
      state = PAUSE;
      notifyAll();
      try {
        wait();
      }
      catch (InterruptedException ie) {}
      return true;
    }
    public synchronized boolean stop () {
      state = STOP;
      notifyAll();

      if (thread == null)
        return true;

      try {
        thread.join();
	thread = null;
      }
      catch (InterruptedException ie) {}

      return true;
    }
  }

  protected int doSync (Buffer buf)
  {
    return Clock.OK;
  }
  protected boolean doEvent (Event event)
  {
    switch (event.getType()) {
      case Event.FLUSH_START:
        ringBuffer.pause();
        break;
      case Event.FLUSH_STOP:
        break;
      case Event.NEWSEGMENT:
        break;
      case Event.EOS:
        break;
    }
    return true;
  }
  protected int render (Buffer buf)
  {
    int result;

    result = ringBuffer.commit (buf.data, buf.time_offset, buf.offset, buf.length);
    if (result < 0) {
      return Pad.WRONG_STATE;
    }
    return Pad.OK;
  }

  protected boolean setCaps (Caps caps)
  {
    boolean res;

    ringBuffer.release();
    res = ringBuffer.acquire(caps);
    return res;
  }

  protected int changeState (int transition) {
    int result;

    switch (transition) {
      case STOP_PAUSE:
        ringBuffer = createRingBuffer();
        break;
      case PAUSE_PLAY:
        break;
    }
    result = super.changeState(transition);

    switch (transition) {
      case PLAY_PAUSE:
        ringBuffer.pause();
        reset();
        break;
      case PAUSE_STOP:
        ringBuffer.stop();
        ringBuffer.release();
        break;
    }

    return result;
  }

  protected abstract RingBuffer createRingBuffer();
  protected abstract boolean open (RingBuffer ring);
  protected abstract boolean close (RingBuffer ring);
  protected abstract int write (byte[] data, int offset, int length);
  protected abstract long delay ();
  protected abstract void reset ();
}