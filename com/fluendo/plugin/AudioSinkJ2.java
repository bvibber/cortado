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

import com.fluendo.jst.*;
import com.fluendo.utils.*;
import javax.sound.sampled.*;

public class AudioSinkJ2 extends AudioSink
{
  private SourceDataLine line = null;
  private int channels;

  private static final int DELAY = 4096;

  protected RingBuffer createRingBuffer() {
    return new RingBuffer();
  }

  protected boolean open (RingBuffer ring) {
    channels = ring.channels;

    AudioFormat format = new AudioFormat(ring.rate, 16, ring.channels, true, true);
    DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

    try {
      line = (SourceDataLine) AudioSystem.getLine(info);
      line.open(format);
    }
    catch (javax.sound.sampled.LineUnavailableException e) {
      e.printStackTrace();
      postMessage (Message.newError (this, e.getMessage()));
      return false;
    }

    Debug.log(Debug.INFO, "line info: available: "+ line.available());
    Debug.log(Debug.INFO, "line info: buffer: "+ line.getBufferSize());
    Debug.log(Debug.INFO, "line info: framePosition: "+ line.getFramePosition());

    ring.segSize = SEGSIZE;
    ring.segTotal = line.getBufferSize() / ring.segSize;
    ring.emptySeg = new byte[ring.segSize];

    line.start();

    return true;
  }

  protected boolean close (RingBuffer ring)
  {
    line.stop();
    line.close();

    return true;
  }

  protected int write (byte[] data, int offset, int length) {
    return line.write (data, offset, length);
  }

  protected long delay () {
    return (line.getBufferSize() - line.available()) / (2 * channels);
  }

  protected void reset () {
    line.flush();
  }

  public String getFactoryName ()
  {
    return "audiosinkj2";
  }

}
