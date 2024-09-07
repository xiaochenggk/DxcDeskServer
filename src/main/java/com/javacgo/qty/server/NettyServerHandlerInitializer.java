package com.javacgo.qty.server;


import com.javacgo.qty.codec.ProtobufFixed32FrameDecoderRedefine;
import com.javacgo.qty.codec.ProtobufFixed32LengthFieldPrependerRedefine;
import com.javacgo.qty.protocol.BigPack;
import com.javacgo.qty.handler.QtyMessageHandler;
import com.javacgo.qty.handler.NettyChannelManager;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.ssl.SslContext;


public class NettyServerHandlerInitializer extends ChannelInitializer<Channel> {
    /**
     * 心跳超时时间
     */
    private static final Integer READ_TIMEOUT_SECONDS = 3 * 60;

    private final SslContext sslCtx;

    NettyChannelManager manager = new NettyChannelManager();

    public NettyServerHandlerInitializer(SslContext sslCtx) {
        this.sslCtx = sslCtx;
    }
    @Override
    protected void initChannel(Channel ch)  {
        // 获得 Channel 对应的 ChannelPipeline
        ChannelPipeline channelPipeline = ch.pipeline();
        // 添加一堆 NettyServerHandler 到 ChannelPipeline 中
        if (sslCtx != null) {
            channelPipeline.addLast(sslCtx.newHandler(ch.alloc()));
        }

        channelPipeline
                // 空闲检测
                //.addLast(new ReadTimeoutHandler(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                // 入站
                .addLast(new ProtobufFixed32FrameDecoderRedefine())
                .addLast("protoBufDecoder",new ProtobufDecoder(BigPack.Exchange.getDefaultInstance()))
                // 出站
                .addLast(new ProtobufFixed32LengthFieldPrependerRedefine())
                .addLast(new ProtobufEncoder())
                // 消息分发器
                .addLast(new QtyMessageHandler(manager))
                // 服务端处理器
//                .addLast(new NettyServerHandler())
        ;
    }

}
