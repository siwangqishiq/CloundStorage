package xyz.panyi.cloudstroage.service;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import xyz.panyi.cloudstroage.AppConfiguration;
import xyz.panyi.cloudstroage.Config;
import xyz.panyi.cloudstroage.util.LogUtil;

import javax.annotation.PostConstruct;

/**
 * 文件云存储服务
 *
 */
@Service
public class CloudFileService {
    private int mPort = Config.CLOUD_FILE_SERVICE_PORT;

    @Autowired
    private AppConfiguration mAppConfiguration;

    @PostConstruct
    public void start(){
        new Thread(()->{
            try {
                startCloudFileService();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } , "CloudFileServerThread").start();
    }

    public void startCloudFileService() throws InterruptedException {


        EventLoopGroup boss = new NioEventLoopGroup();
        EventLoopGroup worker = new NioEventLoopGroup();

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss , worker)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>(){
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast("http-codec", new HttpServerCodec());
                ch.pipeline().addLast("http-aggregator", new HttpObjectAggregator(100 * 1024 * 1024));
                ch.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
                ch.pipeline().addLast("fileServerHandler", new FileServerHandler(mAppConfiguration.getFileRootDir()));
            }
        });
        bootstrap.option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true);

        LogUtil.log("开启云存储服务 listen port : " + mPort);
        ChannelFuture future = bootstrap.bind(mPort).sync();
        future.channel().closeFuture().sync();

        boss.shutdownGracefully();
        worker.shutdownGracefully();
    }
}//end class
