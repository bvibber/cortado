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

public class MultipartDemux extends Element
{
  private Vector streams;

  class MultipartStream extends Pad {
    private boolean needTypefind;
    private String mimeType;

    public MultipartStream (String mime) {
      super (Pad.SRC, "src_"+mime);

      mimeType = mime;
      needTypefind = true;
    }
    protected boolean eventFunc (com.fluendo.jst.Event event) {
      return sinkpad.pushEvent (event);
    }
  }

  private Pad sinkpad = new Pad(Pad.SINK, "sink") {
   
    private MultipartStream findStream (String mime) {
      MultipartStream stream = null;
      for (int i=0; i<streams.size(); i++) {
	stream = (MultipartStream) streams.elementAt(i);
	if (stream.mimeType == mime)
	  break;
	stream = null;
      }
      return stream;
    }
	  
    private boolean forwardEvent (com.fluendo.jst.Event event)
    {
      for (int i=0; i<streams.size(); i++) {
	MultipartStream stream = (MultipartStream) streams.elementAt(i);
	stream.pushEvent (event);
      }
      return true;
    }

    protected boolean eventFunc (com.fluendo.jst.Event event)
    {
      switch (event.getType()) {
        case Event.FLUSH_START:
	  forwardEvent (event);
	  synchronized (streamLock) {
            Debug.log(Debug.INFO, "synced "+this);
	  }
	  break;
        case Event.NEWSEGMENT:
        case Event.FLUSH_STOP:
        case Event.EOS:
	  synchronized (streamLock) {
	    forwardEvent (event);
	  }
	  break;
        default:
	  forwardEvent (event);
	  break;
      }
      return true;
    }
    protected int chainFunc (com.fluendo.jst.Buffer buf)
    {
      int res;
      int flowRet = OK;

      MemUtils.dump (buf.data, buf.offset, buf.length);

      buf.free();

      return flowRet;
    }
  };

  public String getName ()
  {
    return "multipartdemux";
  }
  public String getMime ()
  {
    return "multipart/x-mixed-replace";
  }
  public int typeFind (byte[] data, int offset, int length)
  {
    return -1;
  }

  public MultipartDemux () {
    super ();

    streams = new Vector();

    addPad (sinkpad);
  }
}
