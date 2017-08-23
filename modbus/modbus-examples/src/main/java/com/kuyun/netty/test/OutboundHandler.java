package com.kuyun.netty.test;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

/**
 * Created by user on 5/27/2017.
 */
public class OutboundHandler extends ChannelOutboundHandlerAdapter {

    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ctx.write(msg, promise);
    }
}
