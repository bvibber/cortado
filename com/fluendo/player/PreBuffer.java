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

public class PreBuffer extends InputStream implements Runnable {
  private InputStream inputStream;
  private int bufferSize;
  private byte[] buffer;
  private int in;
  private int out;
  private Reader reader;
  private Thread thread;
  private boolean stopping = false;
  private int filled = 0;
  private PreBufferNotify notify;

  private int state = PreBufferNotify.STATE_START;

  public PreBuffer (InputStream is, int bufSize, PreBufferNotify pbn) {
    inputStream = is;
    bufferSize = bufSize;
    buffer = new byte[bufferSize];
    in = -1;
    out = 0;
    notify = pbn;
    filled = 0;
    thread = new Thread (this);
    thread.start();
  }

  public void run() {
    while (!stopping) {
      try {
        int b = inputStream.read();
        receive (b);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public synchronized boolean isEmpty() {
    return filled < (bufferSize * 10/100);
  }

  public synchronized boolean isFilled() {
    return filled >= (bufferSize * 70/100);
  }

  public synchronized int getFilled() {
    return filled;
  }

  public synchronized void startBuffer() {
    state = PreBufferNotify.STATE_BUFFER;
  }

  public synchronized void receive(int b) {
    while (in == out) {
      if (notify != null)
        notify.preBufferNotify (PreBufferNotify.STATE_OVERFLOW);

      notifyAll();
      try {
	wait (1000);
      }
      catch (InterruptedException ie) {
        ie.printStackTrace();
      }
    }
    if (in < 0) {
      in = 0;
      out = 0;
      filled = 1;
    }
    else {
      filled++;
    }
    buffer[in++] = (byte) (b & 0xff);
    if (in >= buffer.length) {
      in = 0;
    }
    if (state == PreBufferNotify.STATE_BUFFER) {
      if (isFilled()) {
        state = PreBufferNotify.STATE_PLAYBACK;
	if (notify != null)
	  notify.preBufferNotify (state);
      }
    }
  }

  public synchronized int read() {
    /* buffer empty */
    while (state == PreBufferNotify.STATE_BUFFER || in < 0) {
      notifyAll();
      try {
	wait (1000);
      }
      catch (InterruptedException ie) {
        ie.printStackTrace();
      }
    }
    int ret = buffer[out++] & 0xff;
    if (out >= buffer.length) {
      out = 0;
    }
    if (in == out) {
      in = -1;
      filled = -1;
    }
    else {
      filled--;
    }
    if (state == PreBufferNotify.STATE_PLAYBACK) {
      if (isEmpty()) {
        state = PreBufferNotify.STATE_BUFFER;
	if (notify != null)
	  notify.preBufferNotify (state);
      }
    }
    return ret;
  }
}
