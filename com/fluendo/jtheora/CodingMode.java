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

public class CodingMode {
   private int value;

   public static final CodingMode CODE_INTER_NO_MV =
      new CodingMode(0x0); /* INTER prediction, (0,0) motion vector implied.  */

   public static final CodingMode CODE_INTRA =
      new CodingMode(0x1); /* INTRA i.e. no prediction. */

   public static final CodingMode CODE_INTER_PLUS_MV =
      new CodingMode(0x2); /* INTER prediction, non zero motion vector. */

   public static final CodingMode CODE_INTER_LAST_MV =
      new CodingMode(0x3); /* Use Last Motion vector */

   public static final CodingMode CODE_INTER_PRIOR_LAST =
      new CodingMode(0x4); /* Prior last motion vector */

   public static final CodingMode CODE_USING_GOLDEN     =
      new CodingMode(0x5); /* 'Golden frame' prediction (no MV). */

   public static final CodingMode CODE_GOLDEN_MV        =
      new CodingMode(0x6); /* 'Golden frame' prediction plus MV. */

   public static final CodingMode CODE_INTER_FOURMV     =
      new CodingMode(0x7);  /* Inter prediction 4MV per macro block. */

   public static final CodingMode[] MODES = {
      CODE_INTER_NO_MV,
      CODE_INTRA,
      CODE_INTER_PLUS_MV,
      CODE_INTER_LAST_MV,
      CODE_INTER_PRIOR_LAST,
      CODE_USING_GOLDEN,
      CODE_GOLDEN_MV,
      CODE_INTER_FOURMV
   };

   private CodingMode(int i) {
      value=i;
   }

   public int getValue() {
      return value;
   }
}
