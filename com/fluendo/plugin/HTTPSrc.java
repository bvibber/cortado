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

import java.io.*;
import java.net.*;
import com.fluendo.jst.*;
import com.fluendo.utils.*;

public class HTTPSrc extends Element
{
  private Thread thread;
  private boolean stopping;
  private String userId;
  private String password;
  private String urlString;
  private InputStream input;

  private Pad srcpad = new Pad(Pad.SRC, "src") {
    private boolean doSeek (long position) {
      boolean result;

      pushEvent (Event.newFlushStart());

      synchronized (streamLock) {
        try {
          input = getInputStream (position);
          result = true;
        }
        catch (Exception e) {
          result = false;
        }
        pushEvent (Event.newFlushStop());

        pushEvent (Event.newNewsegment(Format.BYTES, position));

        if (result)
	  result = startTask();
      }
      return result;
    }

    protected boolean eventFunc (Event event)
    {
      boolean res;

      switch (event.getType()) {
        case Event.SEEK:
	  res = doSeek(event.getSeekPosition());
	  break;
        default:
          res = super.eventFunc (event);
          break;
      }
      return res;
    }

    protected void taskFunc()
    {
      int ret;

      Buffer data = Buffer.create();
      data.ensureSize (4096);
      data.offset = 0;
      try {
        data.length = input.read (data.data, 0, 4096);
      }
      catch (Exception e) {
	e.printStackTrace();
        data.length = 0;
      }
      if (data.length <= 0) {
	/* EOS */
	data.free();
	pushEvent (Event.newEOS());
	pauseTask();
      }
      else {
        if ((ret = push(data)) != OK) {
	  pauseTask();
        }
      }
    }
    
    protected boolean activateFunc (int mode)
    {
      boolean res = true;

      switch (mode) {
        case MODE_NONE:
	  res = stopTask();
	  break;
        case MODE_PUSH:
	  try {
	    input = getInputStream(0); 
	  }
	  catch (Exception e) {
	    res = false;
	  }
	  if (res)
	    res = startTask();
	  break;
	default:
	  res = false;
	  break;
      }
      return res;
    }
  };

  private InputStream getInputStream (long offset) throws Exception
  {
    InputStream dis;

    try {
      Debug.log(Debug.INFO, "reading from url "+urlString);
      URL url = new URL(urlString);
      Debug.log(Debug.INFO, "trying to open "+url);
      URLConnection uc = url.openConnection();
      if (userId != null && password != null) {
        String userPassword = userId + ":" + password;
        String encoding = Base64Converter.encode (userPassword.getBytes());
        uc.setRequestProperty ("Authorization", "Basic " + encoding);
      }
      uc.setRequestProperty ("Range", "bytes=" + offset+"-");
      /* FIXME, do typefind */
      dis = uc.getInputStream();
      Debug.log(Debug.INFO, "opened "+url);
    }
    catch (SecurityException e) {
      e.printStackTrace();
      postMessage(Message.newError (this, "Not allowed "+urlString+"..."));
      throw e;
    }
    catch (Exception e) {
      e.printStackTrace();
      postMessage(Message.newError (this, "Failed opening "+urlString+"..."));
      throw e;
    }

    return dis;
  }

  public String getName () {
    return "httpsrc";
  }

  public HTTPSrc () {
    super ();
    addPad (srcpad);
  }

  public synchronized boolean setProperty(String name, java.lang.Object value) {
    boolean res = true;

    if (name.equals("url")) {
      urlString = String.valueOf(value);
    }
    else if (name.equals("userId")) {
      userId = String.valueOf(value);
    }
    else if (name.equals("password")) {
      password = String.valueOf(value);
    }
    else {
      res = false;
    }
    return res;
  }
}
