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
  last mod: $Id: toplevel.c,v 1.39 2004/03/18 02:00:30 giles Exp $

 ********************************************************************/

package com.fluendo.jtheora;

public class Version 
{
  public static final int VERSION_MAJOR = 3;
  public static final int VERSION_MINOR = 2;
  public static final int VERSION_SUB = 0;

  private static final String VENDOR_STRING = "Xiph.Org libTheora I 20040317 3 2 0";

  public static String getVersionString()
  {
    return VENDOR_STRING;
  }

  public static int getVersionNumber()
  {
    return (VERSION_MAJOR<<16) + (VERSION_MINOR<<8) + (VERSION_SUB);
  }
}
