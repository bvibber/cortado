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

public class Colorspace {
  private int colorSpace;

  public static final Colorspace UNSPECIFIED = new Colorspace (0);
  public static final Colorspace ITU_REC_470M = new Colorspace (1);
  public static final Colorspace ITU_REC_470BG = new Colorspace (2);

  public static final Colorspace[] spaces = {
    UNSPECIFIED,
    ITU_REC_470M,
    ITU_REC_470BG
  };

  private Colorspace(int cs) {
    colorSpace = cs;
  }
}
