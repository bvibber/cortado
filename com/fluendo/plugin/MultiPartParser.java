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

package com.fluendo.plugin;

import java.io.*;
import java.util.*;
import com.fluendo.player.*;

public class MultiPartParser
{
  private InputStream input;
  private byte[] buffer;
  private int buflen;
  private int scanpos;
  private boolean eos;

  private int readsize = 4096;

  private byte[] boundary;
  private int boundarylen;
  private int matchpos;
  
  public MultiPartParser (InputStream is, String boundaryString)
  {
    input = is;
    boundary = boundaryString.getBytes();
    boundarylen = boundary.length;
    buffer = new byte[readsize];
    reset();
  }

  private void reset()
  {
    buflen = 0;
    scanpos = 0;
    matchpos = 0;
  }
  
  private void flush()
  {
    System.arraycopy(buffer, scanpos, buffer, 0, buflen - scanpos);
    buflen = buflen - scanpos;
    scanpos = 0;
  }
  
  private void fill(int pos) throws IOException
  {
    //System.out.println("fill "+pos);
    if (eos)
      return;
    // make sure buffer is long enough
    if (pos + readsize > buffer.length)
    {
      byte[] newbuf = new byte[(pos + readsize) * 2];
      System.arraycopy(buffer, 0, newbuf, 0, pos);
      buffer = newbuf;
    }

    int read = input.read(buffer, pos, readsize);
    //System.out.println("fill done "+read);
    if (read == -1) {
      eos = true;
      return;
    }
    buflen = pos + read;
  }

  private void findBoundary (boolean scan) throws IOException
  {
    matchpos = 0;

    while (!eos) {
      while (scanpos < buflen) {
        //System.out.print("matching "+buffer[scanpos]+" "+boundary[matchpos]);
        if (buffer[scanpos] == boundary[matchpos]) {
          //System.out.println("..match");
          matchpos++;
          if (matchpos == boundarylen) {
	    scanpos -= boundarylen - 1;
            //System.out.println("..found");
	    return;
          }
        }
        else {
          //System.out.println("..no match");
          matchpos = 0;
        }
	scanpos++;
      }
      if (!scan) {
        fill(buflen);
      }
      else {
        if (matchpos > 0) {
          //System.out.println("found partial match");
	}
        fill(0);
        scanpos = 0;
      }
    }
  }

  public Vector readHeaders() throws IOException
  {
    if (eos)
      return null;

    Vector res = new Vector();
    //System.out.println("finding boundary");
    findBoundary(true);
    //System.out.println("boundary at "+scanpos);
    int lineStart = scanpos;
    while (!eos) {
      while (scanpos < buflen) {
        //System.out.print("matching "+buffer[scanpos]);
        if (buffer[scanpos] == '\n') {
          //System.out.println("..match ");
	  if (lineStart == scanpos) {
	    scanpos++;
            //System.out.println("headers done");
            return res;
	  }
	  String header = new String(buffer, lineStart, scanpos - lineStart);
          //System.out.println("header "+header);
	  res.addElement(header);
	  lineStart = scanpos + 1;
        }
	else {
          //System.out.println("..no match ");
	}
        scanpos++;
      }
      fill(buflen);
    }
    return null;
  }

  public void fillData(DataConsumer consumer) throws IOException
  {
    if (eos)
      return;

    flush();
    //System.out.println("finding data boundary");
    findBoundary(false);
    //System.out.println("finding data boundary done "+scanpos);

    if (consumer != null) {
      int end = scanpos;
      if (!eos) end-=1;
      MediaBuffer buf = MediaBuffer.create();
      buf.copyData (buffer, 0, end);

      //System.out.println("sending data to consumer");

      consumer.consume(buf);
    }
  }
  
}
