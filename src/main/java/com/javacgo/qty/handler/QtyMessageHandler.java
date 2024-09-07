package com.javacgo.qty.handler;

import com.javacgo.qty.protocol.BigPack;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class QtyMessageHandler extends SimpleChannelInboundHandler<BigPack.Exchange> {

    private Logger logger = LoggerFactory.getLogger(getClass());

    private NettyChannelManager channelManager;

    public QtyMessageHandler(NettyChannelManager channelManager) {
        this.channelManager = channelManager;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BigPack.Exchange msg) throws Exception {
        BigPack.Exchange.DataType dataType = msg.getDataType();

        if (dataType == BigPack.Exchange.DataType.REGISTER) {
            channelManager.addHost(msg, ctx.channel());
            return;
        }
        //其他情况都要验证来源ID
        if (!channelManager.validResourceID(msg)) {
            return;
        }
        switch (dataType){
            case QUERY_HOST:
                channelManager.queryHost(msg);
                break;
            case REQUEST_AUTH:
                //转发
                String targetID = msg.getTargetID();
                channelManager.send(targetID, msg);
                break;
            case RESPONSE_AUTH:
                channelManager.responseAuth(msg);
                break;
            case GIVE_IMG_PARA:
            case GIVE_IMG:
                channelManager.PASendAll(msg);
                break;
            case GET_DESKTOP:
            case TILE_RECEIVED:
            case MOUSE_MOVE:
            case MOUSE_KEY:
            case WHEEL_EVENT:
            case KEY_BOARD:
                //转发
                channelManager.APSend(msg);
                break;
            default:
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 从管理器中添加
        logger.info("[channelActive][一个连接({})加入]", ctx.channel().id());
    }

    @Override
    public void channelUnregistered(ChannelHandlerContext ctx) {
        // 从管理器中移除
        logger.info("[channelUnregistered][一个连接({})离开]", ctx.channel().id());
        channelManager.removeHost(ctx.channel());
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("[exceptionCaught][连接({}) 发生异常]", ctx.channel().id(), cause);
        // 断开连接
        ctx.channel().close();
    }
}
