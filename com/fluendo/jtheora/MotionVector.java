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
  last mod: $Id: pb.c,v 1.7 2003/12/06 18:06:20 arc Exp $

 ********************************************************************/

package com.fluendo.jtheora;

public class MotionVector extends Coordinate 
{
  public static final MotionVector NULL = new MotionVector (0,0);

  public MotionVector () {
    super();
  }
  public MotionVector (int x, int y) {
    super(x,y);
  }
}
