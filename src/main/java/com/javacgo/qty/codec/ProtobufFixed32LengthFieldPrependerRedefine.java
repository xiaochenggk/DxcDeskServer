package com.javacgo.qty.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class ProtobufFixed32LengthFieldPrependerRedefine extends MessageToByteEncoder<ByteBuf> {

    public ProtobufFixed32LengthFieldPrependerRedefine() {
    }

    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        int bodyLen = msg.readableBytes();
        int headerLen = 4;
        out.ensureWritable(headerLen + bodyLen);  //前4个字节+数据长度
        writeRawVarint32(out, bodyLen);  //把body的长度写到前四个字节，int转为网络需
        out.writeBytes(msg, msg.readerIndex(), bodyLen);
    }

    static void writeRawVarint32(ByteBuf out, int value) {

        byte[] frontBytes = intToBytes(value);  //int转为网络序

        out.writeBytes(frontBytes);
    }

    //写入的时候，把 int 转化为网络序
    public static byte[] intToBytes(int n) {
        byte[] b = new byte[4];
        b[3] = (byte) (n & 0xff);
        b[2] = (byte) (n >> 8 & 0xff);
        b[1] = (byte) (n >> 16 & 0xff);
        b[0] = (byte) (n >> 24 & 0xff);
        return b;
    }
}