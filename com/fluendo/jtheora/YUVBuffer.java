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

import java.awt.*;
import java.awt.image.*;

public class YUVBuffer
{
  public int y_width;
  public int y_height;
  public int y_stride;

  public int uv_width;
  public int uv_height;
  public int uv_stride;

  public short[] data;
  public int y_offset;
  public int u_offset;
  public int v_offset;

  private int[] pixels;
  private int pix_size;

  private void prepareRGBData ()
  {
    int size = y_width * y_height;
    if (size != pix_size) {
      pixels = new int[size];
      pix_size = size;
    }
    YUVtoRGB ();
  }

  public int[] getAsRGBData ()
  {
    prepareRGBData();
    return pixels;
  }

  public Image getAsImage (Toolkit toolkit)
  {
    prepareRGBData();

    MemoryImageSource source =
      new MemoryImageSource (y_width, y_height, pixels, 0, y_width);

    return toolkit.createImage (source);
  }

  private static final double YFACTOR = 0.8588235;
  private static final int VAL_RANGE = 256;

  // RGB and YUV accelerator structures.
  private static int[] CalcUTable = new int[VAL_RANGE * 2];
  private static int[] CalcRUTable = new int[VAL_RANGE];
  private static int[] CalcVTable = new int[VAL_RANGE * 2];
  private static int[] CalcRVTable = new int[VAL_RANGE];
  private static int[] InvYScale = new int[VAL_RANGE * 2];
  private static int[] DivBy5p87 = new int[VAL_RANGE * 14];
  private static int[] Times2p99 = new int[VAL_RANGE];
  private static int[] Times5p87 = new int[VAL_RANGE];
  private static int[] Times1p14 = new int[VAL_RANGE];
  private static int[] LimitVal = new int[VAL_RANGE * 3];

  static {
    SetupRgbYuvAccelerators ();
  }

  private void Raw () 
  {
    int off, y = 0;

    off = y_offset;
    for (int i=0; i<y_height; i++) {
      for (int j=0; j<y_width; j++) {
        int pixel = data[off+j];
        pixels[y++] = (pixel<<24) | (pixel<<16) | (pixel<<8) | pixel;
      }
      off += y_stride;
    }
    off = u_offset;
    for (int i=0; i<y_height/2; i++) {
      for (int j=0; j<y_width/2; j++) {
        int pixel = data[off+j];
        pixels[y++] = (pixel<<24) | (pixel<<16) | (pixel<<8) | pixel;
      }
      off += uv_stride;
    }
    off = v_offset;
    for (int i=0; i<y_height/2; i++) {
      for (int j=0; j<y_width/2; j++) {
        int pixel = data[off+j];
        pixels[y++] = (pixel<<24) | (pixel<<16) | (pixel<<8) | pixel;
      }
      off += uv_stride;
    }
  }

  private void YUVtoRGB () 
  {
    int UFactor;
    int VFactor;
    int YVal;
    int RVal;
    int BVal;

    // Set up starting values for YUV pointers
    int YPtr = y_offset;
    int YPtr2 = y_offset + y_stride;
    int UPtr = u_offset;
    int VPtr = v_offset;
    int RGBPtr = 0;
    int RGBPtr2 = y_width;

    // Set the line step for the Y and UV planes and YPtr2
    int YStep = y_stride * 2;
    int UVStep = uv_stride;
    int RGBStep = y_width;

    for (int i=0; i < y_height / 2; i++)
    {
      for (int j=0, l=0; j < y_width / 2; j++, l+=2) {
	// groups of four pixels
	UFactor = CalcRUTable[data[UPtr+j]] + VAL_RANGE;
	VFactor = CalcRVTable[data[VPtr+j]] + VAL_RANGE;

	YVal = InvYScale[data[YPtr+l]];
	RVal = LimitVal[YVal + VFactor];
	BVal = LimitVal[YVal + UFactor];
	pixels[RGBPtr++] = (RVal<<16) | 
	         DivBy5p87[(VAL_RANGE*4)+ 10*YVal - Times2p99[RVal] - Times1p14[BVal]] | BVal;

	YVal = InvYScale[data[YPtr+l+1]];
	RVal = LimitVal[YVal + VFactor];
	BVal = LimitVal[YVal + UFactor];
	pixels[RGBPtr++] = (RVal<<16) |
	         DivBy5p87[(VAL_RANGE*4)+ 10*YVal - Times2p99[RVal] - Times1p14[BVal]] | BVal;

	YVal = InvYScale[data[YPtr2+l]];
	RVal = LimitVal[YVal + VFactor];
	BVal = LimitVal[YVal + UFactor];
	pixels[RGBPtr2++] = (RVal<<16) |
	         DivBy5p87[(VAL_RANGE*4)+ 10*YVal - Times2p99[RVal] - Times1p14[BVal]] | BVal;

	YVal = InvYScale[data[YPtr2+l+1]];
	RVal = LimitVal[YVal + VFactor];
	BVal = LimitVal[YVal + UFactor];
	pixels[RGBPtr2++] = (RVal<<16) |
	         DivBy5p87[(VAL_RANGE*4)+ 10*YVal - Times2p99[RVal] - Times1p14[BVal]] | BVal;
      }

      // Increment the various pointers
      YPtr += YStep;
      YPtr2 += YStep;
      UPtr += UVStep;
      VPtr += UVStep;
      RGBPtr += RGBStep;
      RGBPtr2 += RGBStep;
    }
  }

  private static int Clamp255(double x) {
    return (int) ((x) < 0 ? 0 : ((x) <= 255 ? (x) : 255));
  }

  private static void SetupRgbYuvAccelerators ()
  {
    int i;

    for( i = 0; i < VAL_RANGE * 3; i++) {
      LimitVal[ i] = Clamp255( i - VAL_RANGE);
    }

    // V range raw is +/- 179.
    // Correct to approx 16-240 by dividing by 1.596 and adding 128.

    // U range raw is +/- 226.
    // Correct to approx 16-240 by dividing by 2.018 and adding 128.

    for (i = -VAL_RANGE; i < VAL_RANGE; i++)
      {
	int x;
	if (i < 0)
	  {
	    x = (int) ((i / 1.596) - 0.5) + 128;
	    CalcVTable[i + VAL_RANGE] = Clamp255 (x);
	    CalcUTable[i + VAL_RANGE] = (short) (((i / 2.018) - 0.5) + 128);
	  }
	else
	  {
	    x = (int) ((i / 1.596) + 0.5) + 128;
	    CalcVTable[i + VAL_RANGE] = Clamp255 (x);
	    CalcUTable[i + VAL_RANGE] = (short) (((i / 2.018) + 0.5) + 128);
	  }
      }

    for (i = 0; i < (VAL_RANGE * 14); i++)
      {
	int x = (i - (VAL_RANGE * 4));

	if (x < 0)
	  x = (int) ((x / 5.87) - 0.5);
	else
	  x = (int) ((x / 5.87) + 0.5);

	DivBy5p87[i] = (Clamp255 (x) << 8) | (255<<24);
      }

    for (i = 0; i < VAL_RANGE; i++)
      {
	InvYScale[i] = Clamp255 ((i - 16) / YFACTOR);
	if (i < 128)
	  {
	    CalcRVTable[i] = (int) (((i - 128) * 1.596) - 0.5);
	    CalcRUTable[i] = (int) (((i - 128) * 2.018) - 0.5);
	  }
	else
	  {
	    CalcRVTable[i] = (int) (((i - 128) * 1.596) + 0.5);
	    CalcRUTable[i] = (int) (((i - 128) * 2.018) + 0.5);
	  }
	Times2p99[i] = (int) ((i * 2.99) + 0.5);
	Times5p87[i] = (int) ((i * 5.87) + 0.5);
	Times1p14[i] = (int) ((i * 1.14) + 0.5);
      }
  }
}
