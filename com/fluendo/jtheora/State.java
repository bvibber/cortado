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

import com.jcraft.jogg.*;

public class State 
{
  long granulepos;

  private Playback pbi;
  private Decode dec;
  private boolean need_keyframe;

  public void clear()
  {
    if(pbi != null){
      pbi.info.clear();
      pbi.clearHuffmanSet();
      FrInit.ClearFragmentInfo(pbi);
      FrInit.ClearFrameInfo(pbi);
      pbi.clear();
    }
    pbi = null;
  }

  public int decodeInit(Info ci)
  {
    pbi = new Playback(ci);
    dec = new Decode(pbi);
    granulepos=-1;
    need_keyframe = true;

    return(0);
  }

  public int decodePacketin (Packet op)
  {
    long ret;

    pbi.DecoderErrorCode = 0;

    pbi.opb.readinit(op.packet_base, op.packet, op.bytes);

    /* verify that this is a video frame */
    ret = pbi.opb.readB(1);

    if (ret==0) {
      if (need_keyframe) {
        if ((op.packet_base[op.packet] & 0x40) == 0) {
	  need_keyframe = false;
	}
	else {
	  return 0;
	}
      }

      ret=dec.loadAndDecode(pbi);

      if(ret != 0)
        return (int) ret;
 

      //if(pbi.PostProcessingLevel != 0)
      //  pbi.PostProcess();

      if(op.granulepos>-1)
        granulepos=op.granulepos;
      else{
        if(granulepos==-1){
          granulepos=0;
        } 
	else {
          if (pbi.FrameType == Constants.BASE_FRAME){
            long frames= granulepos & ((1<<pbi.keyframe_granule_shift)-1);
            granulepos>>=pbi.keyframe_granule_shift;
            granulepos+=frames+1;
            granulepos<<=pbi.keyframe_granule_shift;
          }else
            granulepos++;
        }
      }
  
      return(0);
    }

    return Result.BADPACKET;
  }

  public int decodeYUVout (YUVBuffer yuv)
  {
    yuv.y_width = pbi.info.width;
    yuv.y_height = pbi.info.height;
    yuv.y_stride = pbi.YStride;

    yuv.uv_width = pbi.info.width / 2;
    yuv.uv_height = pbi.info.height / 2;
    yuv.uv_stride = pbi.UVStride;

    if(pbi.PostProcessingLevel != 0){
      yuv.data = pbi.PostProcessBuffer;
    }else{
      yuv.data = pbi.LastFrameRecon;
    }
    yuv.y_offset = pbi.ReconYDataOffset;
    yuv.u_offset = pbi.ReconUDataOffset;
    yuv.v_offset = pbi.ReconVDataOffset;
  
    /* we must flip the internal representation,
       so make the stride negative and start at the end */
    yuv.y_offset += yuv.y_stride * (yuv.y_height - 1);
    yuv.u_offset += yuv.uv_stride * (yuv.uv_height - 1);
    yuv.v_offset += yuv.uv_stride * (yuv.uv_height - 1);
    yuv.y_stride = - yuv.y_stride;
    yuv.uv_stride = - yuv.uv_stride;
  
    return 0;
  }

  /* returns, in seconds, absolute time of current packet in given
     logical stream */
  public double granuleTime(long granulepos)
  {
    if(granulepos>=0){
      long iframe=granulepos>>pbi.keyframe_granule_shift;
      long pframe=granulepos-(iframe<<pbi.keyframe_granule_shift);

      return (iframe+pframe)*
        ((double)pbi.info.fps_denominator/pbi.info.fps_numerator);
    }
    return(-1);
  }
}
