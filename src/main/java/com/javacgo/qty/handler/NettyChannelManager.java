package com.javacgo.qty.handler;

import com.javacgo.qty.entity.QtyDesk;
import com.javacgo.qty.protocol.BigPack;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 客户端 Channel 管理器。提供两种功能：
 * 1. 客户端 Channel 的管理
 * 2. 向客户端 Channel 发送消息
 */
@Component
public class NettyChannelManager {

    //日志
    private Logger logger = LoggerFactory.getLogger(getClass());

    public enum CONNECT_TYPE {
        SESSION1(0), SESSION0(1), ACTIVE(2);
        int type;
        CONNECT_TYPE(int type) {
            this.type = type;
        }
        public int getType() {
            return type;
        }
    }

    //设备ID 映射到 DeskDesk实体
    private ConcurrentMap<String, QtyDesk> deviceIDMap = new ConcurrentHashMap<>();
    //channelID 映射到 设备ID
    private ConcurrentMap<ChannelId, String> channelIDMap = new ConcurrentHashMap<>();
    //验证resourceID是否存在，相当验证TOKEN
    public boolean validResourceID(BigPack.Exchange msg) {
        String id = msg.getResourceID();
        if (deviceIDMap.containsKey(id)) {
            return true;
        } else {
            return false;
        }
    }

    public boolean addHost(BigPack.Exchange msg, Channel channel) {
        BigPack.CsRegisterHost csHostInfo = msg.getHostInfo();
        boolean ifOK = false;
        String deviceId = "";
        //唯一ID
        if (CONNECT_TYPE.SESSION1.getType() == csHostInfo.getType()) {
            deviceId = new BigInteger(1, csHostInfo.getUniqueID().getBytes())
                    .toString().substring(0, 9);
        } else if (CONNECT_TYPE.ACTIVE.getType() == csHostInfo.getType()) {
            deviceId = channel.id().asShortText();
        }
        logger.info("[addHost] 注册主机：" + deviceId);
        if (deviceIDMap.containsKey(deviceId)) {
            logger.error("[addHost] 设备已经登录过");
        } else {
            ifOK = true;
        }
        if (ifOK) {
            QtyDesk qtyDesk = new QtyDesk();
            qtyDesk.setDeviceId(csHostInfo.getDeviceID());
            qtyDesk.setCpuId(csHostInfo.getCpuID());
            qtyDesk.setMac(csHostInfo.getMac());
            qtyDesk.setType(csHostInfo.getType());
            qtyDesk.setChannel(channel);
            deviceIDMap.put(deviceId, qtyDesk);
            channelIDMap.put(channel.id(), deviceId);
        }
        //回复
        BigPack.Exchange.Builder exB = BigPack.Exchange.newBuilder();
        exB.setDataType(BigPack.Exchange.DataType.REPLY_REGISTER);
        exB.setReplyInfo(BigPack.ScReplyRegister.newBuilder().setSuccess(ifOK).setRegisterId(deviceId));
        channel.writeAndFlush(exB.build());
        return ifOK;
    }

    public void removeHost(Channel channel) {
        logger.info("[removeHost] channel.id:" + channel.id());
        // 移除 channels
        String deviceID = channelIDMap.get(channel.id());

        for(QtyDesk desk : deviceIDMap.values()){
            List<QtyDesk> authList = desk.getAuthList();
            for(QtyDesk authDesk : authList){
                if(authDesk.getChannel() == channel){
                    authList.remove(authDesk);
                    break;
                }
            }
        }
        channelIDMap.remove(channel.id());
        deviceIDMap.remove(deviceID);
        logger.info("[removeHost] deviceID:" + deviceID);
    }
    //查询主机
    public void queryHost(BigPack.Exchange msg) {
        boolean exit = false;
        String queryMessage = "查询主机不存在";
        QtyDesk queryHost = deviceIDMap.get(msg.getTargetID());
        if (!(queryHost == null)) {
            exit = true;
            queryMessage = "查询主机成功";
            //后面可以做个主机在线的查询
        }
        String resourceID = msg.getResourceID();
        BigPack.Exchange.Builder exB = BigPack.Exchange.newBuilder();
        exB.setDataType(BigPack.Exchange.DataType.REPLY_QUERY);
        exB.setReplyQuery(BigPack.ScReplyQuery.newBuilder().setIfExitHost(exit)
                .setQueryMessage(queryMessage));
        send(resourceID, exB.build());
        return;
    }
    //被控发送 认证结果
    public void responseAuth(BigPack.Exchange msg) {
        QtyDesk qtyDeskTarget = deviceIDMap.get(msg.getTargetID());
        QtyDesk qtyDeskResource = deviceIDMap.get(msg.getResourceID());
        if (qtyDeskTarget == null || qtyDeskResource == null) {
            logger.error("[responseAuth] 出错");
            return;
        }
        if (msg.getResponseAuth().getSuccess()) {
            //建立认证列表
            qtyDeskResource.getAuthList().add(qtyDeskTarget);
        }
        send(msg.getTargetID(), msg);
    }

    //普通的发送
    public void send(String deviceID, BigPack.Exchange msg) {
        String targetID = msg.getTargetID();
        // 获得用户对应的 Channel
        QtyDesk qtyDesk = deviceIDMap.get(deviceID);
        if (qtyDesk == null) {
            logger.error("[send][连接不存在]");
            return;
        }
        if (!qtyDesk.getChannel().isActive()) {
            logger.error("[send][连接({})未激活]", qtyDesk.getChannel().id());
            return;
        }
        // 发送消息
        qtyDesk.getChannel().writeAndFlush(msg);
    }
    public void PASendAll(BigPack.Exchange msg){
        // 获得用户对应的 Channel
        String resourceID = msg.getResourceID();
        QtyDesk resourceDesk = deviceIDMap.get(resourceID);
        List<QtyDesk> authList = resourceDesk.getAuthList();
        if(0 == authList.size()){
            //发送停止
            System.out.println("该停止了");
        }
        for(QtyDesk authDesk : authList){
            authDesk.getChannel().writeAndFlush(msg);
        }
    }
    //主控向被控发送消息，需要认证后
    public void APSend(BigPack.Exchange msg) {
        // 获得用户对应的 Channel
        String resourceID = msg.getResourceID();
        String targetID = msg.getTargetID();
        QtyDesk targetDesk = deviceIDMap.get(targetID);
        QtyDesk resourceDesk = deviceIDMap.get(resourceID);
        if (targetDesk == null || resourceDesk==null) {
            logger.error("[send][连接不存在]");
            return;
        }
        if(!targetDesk.getAuthList().contains(resourceDesk)){
            logger.error(resourceID+ " 非法请求--》"+ targetID+ "类型为："+msg.getDataType());
            return;
        }
        if (!targetDesk.getChannel().isActive()) {
            logger.error("[send][连接({})未激活]", targetDesk.getChannel().id());
            return;
        }
        // 发送消息
        targetDesk.getChannel().writeAndFlush(msg);
    }
}
