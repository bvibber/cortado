/********************************************************************
 *                                                                  *
 * THIS FILE IS PART OF THE OggTheora SOFTWARE CODEC SOURCE CODE.   *
 * USE, DISTRIBUTION AND REPRODUCTION OF THIS LIBRARY SOURCE IS     *
 * GOVERNED BY A BSD-STYLE SOURCE LICENSE INCLUDED WITH THIS SOURCE *
 * IN 'COPYING'. PLEASE READ THESE TERMS BEFORE DISTRIBUTING.       *
 *                                                                  *
 * THE Theora SOURCE CODE IS COPYRIGHT (C) 2002-2003                *
 * by the Xiph.Org Foundation http://www.xiph.org/                  *
 *                                                                  *
 ********************************************************************

  function:
  last mod: $Id: encoder_internal.h,v 1.21 2004/03/09 02:02:56 giles Exp $

 ********************************************************************/

package com.fluendo.jtheora;

import com.jcraft.jogg.*;

public class HuffEntry 
{
  HuffEntry[] Child = new HuffEntry[2];
  HuffEntry previous;
  HuffEntry next;
  int       value;
  long      frequency;

  public HuffEntry copy() 
  {
    HuffEntry huffDst;
    huffDst = new HuffEntry();
    huffDst.value = value;
    if (value < 0) {
      huffDst.Child[0] = Child[0].copy();
      huffDst.Child[1] = Child[1].copy();
    }
    return huffDst;
  }

  public int read(int depth, Buffer opb) 
  {
    int bit;
    int ret;

    bit = opb.readB(1);
    if(bit < 0) {
      return Result.BADHEADER;
    }
    else if(bit == 0) {
      if (++depth > 32) 
        return Result.BADHEADER;

      Child[0] = new HuffEntry();
      ret = Child[0].read(depth, opb);
      if (ret < 0) 
        return ret;

      Child[1] = new HuffEntry();
      ret = Child[1].read(depth, opb);
      if (ret < 0) 
        return ret;

      value = -1;
    } 
    else {
      Child[0] = null;
      Child[1] = null;
      value = opb.readB(5);
      if (value < 0) 
        return Result.BADHEADER;
    }
    return 0;
  }
}
