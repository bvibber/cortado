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
  last mod: $Id: theora.h,v 1.18 2004/03/09 06:18:44 msmith Exp $

 ********************************************************************/

package com.fluendo.jtheora;

public final class Result {
  public static final int FAULT     = -1;
  public static final int EINVAL    = -10;
  public static final int BADHEADER = -20;
  public static final int NOTFORMAT = -21;
  public static final int VERSION   = -22;
  public static final int IMPL      = -23;
  public static final int BADPACKET = -24;
  public static final int NEWPACKET = -25;
}
