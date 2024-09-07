package com.javacgo.qty.entity;

import io.netty.channel.Channel;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode
public class QtyDesk {
    //
    String cpuId ;
    //deviceId
    String deviceId;
    //mac
    String mac ;
    //主机名
    String pcName ;
    //注册类型
    int type ;
    //
    Channel channel ;
    //这里不知道需要需要线程安全list类
    List<QtyDesk> authList = new ArrayList();
}
