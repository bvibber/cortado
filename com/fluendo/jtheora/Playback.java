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

import com.jcraft.jogg.*;

public class Playback 
{
  private static final int Q_TABLE_SIZE = 64;

  /* Different key frame types/methods */
  private static final int DCT_KEY_FRAME = 0;

  //oggpack_buffer *opb;
  Buffer  opb = new Buffer();;
  Info     info;
  /* how far do we shift the granulepos to seperate out P frame counts? */
  int             keyframe_granule_shift;


  /***********************************************************************/
  /* Decoder and Frame Type Information */

  int           DecoderErrorCode;
  int           FramesHaveBeenSkipped;

  int           PostProcessEnabled;
  int           PostProcessingLevel;    /* Perform post processing */

  /* Frame Info */
  CodingMode   	codingMode;
  byte 		FrameType;
  byte 		KeyFrameType;
  int           QualitySetting;
  int           FrameQIndex;            /* Quality specified as a
                                           table index */
  int           ThisFrameQualityValue;  /* Quality value for this frame  */
  int           LastFrameQualityValue;  /* Last Frame's Quality */
  int     	CodedBlockIndex;        /* Number of Coded Blocks */
  int           CodedBlocksThisFrame;   /* Index into coded blocks */
  int           FrameSize;              /* The number of bytes in the frame. */

  /**********************************************************************/
  /* Frame Size & Index Information */

  int           YPlaneSize;
  int           UVPlaneSize;
  int           YStride;
  int           UVStride;
  int           VFragments;
  int           HFragments;
  int           UnitFragments;
  int           YPlaneFragments;
  int           UVPlaneFragments;

  int           ReconYPlaneSize;
  int           ReconUVPlaneSize;

  int           YDataOffset;
  int           UDataOffset;
  int           VDataOffset;
  int           ReconYDataOffset;
  int           ReconUDataOffset;
  int           ReconVDataOffset;
  int           YSuperBlocks;   /* Number of SuperBlocks in a Y frame */
  int           UVSuperBlocks;  /* Number of SuperBlocks in a U or V frame */
  int           SuperBlocks;    /* Total number of SuperBlocks in a
                                   Y,U,V frame */

  int           YSBRows;        /* Number of rows of SuperBlocks in a
                                   Y frame */
  int           YSBCols;        /* Number of cols of SuperBlocks in a
                                   Y frame */
  int           UVSBRows;       /* Number of rows of SuperBlocks in a
                                   U or V frame */
  int           UVSBCols;       /* Number of cols of SuperBlocks in a
                                   U or V frame */

  int           YMacroBlocks;   /* Number of Macro-Blocks in Y component */
  int           UVMacroBlocks;  /* Number of Macro-Blocks in U/V component */
  int           MacroBlocks;    /* Total number of Macro-Blocks */

  /**********************************************************************/
  /* Frames  */
  short[] 	ThisFrameRecon;
  short[] 	GoldenFrame;
  short[] 	LastFrameRecon;
  short[] 	PostProcessBuffer;

  /**********************************************************************/
  /* Fragment Information */
  int[]         pixel_index_table;        /* start address of first
                                              pixel of fragment in
                                              source */
  int[]		recon_pixel_index_table;  /* start address of first
                                              pixel in recon buffer */

  byte[] 	display_fragments;        /* Fragment update map */
  int[]  	CodedBlockList;           /* A list of fragment indices for
                                              coded blocks. */
  MotionVector[] FragMVect;                /* fragment motion vectors */

  int[]         FragTokenCounts;          /* Number of tokens per fragment */
  int[]		FragQIndex;               /* Fragment Quality used in
                                              PostProcess */

  byte[] 	FragCoefEOB;               /* Position of last non 0 coef
                                                within QFragData */
  short[][] 	QFragData;            /* Fragment Coefficients
                                               Array Pointers */
  CodingMode[] 	FragCodingMethod;          /* coding method for the
                                               fragment */

  /***********************************************************************/
  /* Macro Block and SuperBlock Information */
  BlockMapping  BlockMap;          /* super block + sub macro
                                                   block + sub frag ->
                                                   FragIndex */

  /* Coded flag arrays and counters for them */
  byte[] 	SBCodedFlags;
  byte[] 	SBFullyFlags;
  byte[] 	MBCodedFlags;
  byte[] 	MBFullyFlags;

  /**********************************************************************/

  Coordinate[]  FragCoordinates;
  FrArray 	frArray = new FrArray();
  Filter 	filter = new Filter();

  int[] 	QThreshTable = new int[Constants.Q_TABLE_SIZE];
  short[] 	DcScaleFactorTable = new short[Constants.Q_TABLE_SIZE];
  short[] 	Y_coeffs = new short[64];
  short[] 	UV_coeffs = new short[64];
  short[] 	Inter_coeffs = new short[64];

  /* Dequantiser and rounding tables */
  short[] 	dequant_InterUV_coeffs;
  int[]   	quant_index = new int[64];
  int[]   	quant_Y_coeffs = new int[64];
  int[]    	quant_UV_coeffs = new int[64];

  HuffEntry[]   HuffRoot_VP3x = new HuffEntry[Huffman.NUM_HUFF_TABLES];
  int[][] 	HuffCodeArray_VP3x;
  byte[][] 	HuffCodeLengthArray_VP3x;
  byte[]   	ExtraBitLengths_VP3x;

  /* Quantiser and rounding tables */
  short[]	dequant_Y_coeffs;
  short[] 	dequant_UV_coeffs;
  short[] 	dequant_Inter_coeffs;
  short[] 	dequant_coeffs;

  public void clearTmpBuffers()
  {
    dequant_Y_coeffs = null;
    dequant_UV_coeffs = null;
    dequant_InterUV_coeffs = null;
    dequant_Inter_coeffs = null;
  }

  private void initTmpBuffers()
  {

    /* clear any existing info */
    clearTmpBuffers();

    /* Adjust the position of all of our temporary */
    dequant_Y_coeffs     = new short[64];
    dequant_UV_coeffs    = new short[64];
    dequant_Inter_coeffs = new short[64];
    dequant_InterUV_coeffs = new short[64];
  }

  public void clear()
  {
    clearTmpBuffers();
    if (opb != null) {
      opb = null;
    }
  }

  private static int ilog (long v)
  {
    int ret=0;

    while (v != 0) {
      ret++;
      v>>=1;
    }
    return ret;
  }

  public Playback (Info ci)
  {
    info = ci;

    initTmpBuffers();

    DecoderErrorCode = 0;
    KeyFrameType = DCT_KEY_FRAME;
    FramesHaveBeenSkipped = 0;

    FrInit.InitFrameDetails(this);

    keyframe_granule_shift = ilog(ci.keyframe_frequency_force-1);
    LastFrameQualityValue = 0;

    /* Initialise version specific quantiser and in-loop filter values */
    copyQTables(ci);
    filter.copyFilterTables(ci);

    /* Huffman setup */
    initHuffmanTrees(ci);
  }

  public int getFrameType() {
    return FrameType;
  }

  void setFrameType(byte FrType ){
    /* Set the appropriate frame type according to the request. */
    switch ( FrType ){
      case Constants.BASE_FRAME:
        FrameType = FrType;
	break;
      default:
        FrameType = FrType;
        break;
    }
  }

  public void clearHuffmanSet()
  {
    Huffman.clearHuffmanTrees(HuffRoot_VP3x);

    HuffCodeArray_VP3x = null;
    HuffCodeLengthArray_VP3x = null;
  }

  public void initHuffmanSet()
  {
    clearHuffmanSet();

    ExtraBitLengths_VP3x = HuffTables.ExtraBitLengths_VP31;

    HuffCodeArray_VP3x = new int[Huffman.NUM_HUFF_TABLES][Huffman.MAX_ENTROPY_TOKENS];
    HuffCodeLengthArray_VP3x = new byte[Huffman.NUM_HUFF_TABLES][Huffman.MAX_ENTROPY_TOKENS];

    for (int i = 0; i < Huffman.NUM_HUFF_TABLES; i++ ){
      Huffman.buildHuffmanTree(HuffRoot_VP3x,
                       HuffCodeArray_VP3x[i],
                       HuffCodeLengthArray_VP3x[i],
                       i, HuffTables.FrequencyCounts_VP3[i]);
    }
  }

  public int readHuffmanTrees(Info ci, Buffer opb) {
    int i;
    for (i=0; i<Huffman.NUM_HUFF_TABLES; i++) {
       int ret;
       ci.HuffRoot[i] = new HuffEntry();
       ret = ci.HuffRoot[i].read(0, opb);
       if (ret != 0) 
         return ret;
    }
    return 0;
  }

  public void initHuffmanTrees(Info ci) 
  {
    int i;
    ExtraBitLengths_VP3x = HuffTables.ExtraBitLengths_VP31;
    for(i=0; i<Huffman.NUM_HUFF_TABLES; i++){
      HuffRoot_VP3x[i] = ci.HuffRoot[i].copy();
    }
  }

  public void copyQTables(Info ci) {
    System.arraycopy(ci.QThreshTable, 0, QThreshTable, 0, Constants.Q_TABLE_SIZE);
    System.arraycopy(ci.DcScaleFactorTable, 0, DcScaleFactorTable,
                   0, Constants.Q_TABLE_SIZE);
    System.arraycopy(ci.Y_coeffs, 0, Y_coeffs, 0, 64);
    System.arraycopy(ci.UV_coeffs, 0, UV_coeffs, 0, 64);
    System.arraycopy(ci.Inter_coeffs, 0, Inter_coeffs, 0, 64);
  }
}
