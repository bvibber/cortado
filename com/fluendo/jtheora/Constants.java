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

public class Constants 
{
  public static final int CURRENT_ENCODE_VERSION =  1;
  public static final int HUGE_ERROR             = (1<<28);  /*  Out of range test value */

/* Baseline dct height and width. */
  public static final int BLOCK_HEIGHT_WIDTH = 8;
  public static final int HFRAGPIXELS        = 8;
  public static final int VFRAGPIXELS        = 8;

/* Baseline dct block size */
  public static final int BLOCK_SIZE         = (BLOCK_HEIGHT_WIDTH * BLOCK_HEIGHT_WIDTH);

/* Border is for unrestricted mv's */
  public static final int UMV_BORDER         = 16;
  public static final int STRIDE_EXTRA       = (UMV_BORDER * 2);
  public static final int Q_TABLE_SIZE       = 64;

  public static final int BASE_FRAME         = 0;
  public static final int NORMAL_FRAME       = 1;

  public static final int MAX_MODES          = 8;
  public static final int MODE_BITS          = 3;
  public static final int MODE_METHODS       = 8;
  public static final int MODE_METHOD_BITS   = 3;

/* Different key frame types/methods */
  public static final int DCT_KEY_FRAME      = 0;

  public static final int KEY_FRAME_CONTEXT  = 5;

/* Preprocessor defines */
  public static final int MAX_PREV_FRAMES    = 16;

/* Number of search sites for a 4-step search (at pixel accuracy) */
  public static final int MAX_SEARCH_SITES   = 33;

  public static final int VERY_BEST_Q        = 10;
  public static final double MIN_BPB_FACTOR     = 0.3;
  public static final double MAX_BPB_FACTOR     = 3.0;

  public static final int MAX_MV_EXTENT      = 31;  /* Max search distance in half pixel increments */

  public static final int[] dequant_index = {
    0,  1,  8,  16,  9,  2,  3, 10,
    17, 24, 32, 25, 18, 11,  4,  5,
    12, 19, 26, 33, 40, 48, 41, 34,
    27, 20, 13,  6,  7, 14, 21, 28,
    35, 42, 49, 56, 57, 50, 43, 36,
    29, 22, 15, 23, 30, 37, 44, 51,
    58, 59, 52, 45, 38, 31, 39, 46,
    53, 60, 61, 54, 47, 55, 62, 63
  };
}
