# 云存储独立服务
包含上传 / 下载的文件云存储服务


### 启动前的配置

##### 上传服务端口
> 配置文件 application.properties

``` server.port=8888 #上传服务的端口  ```  
此时上传服务的地址为 http://localhost:8888/upload

##### 文件下载服务端口
> java文件 xyz.panyi.cloudstroage.Config.java
```java
public final class Config {
    //上传文件目录
    public static final String CLOUD_FILE_SERVICE_DIR = "assets";//文件服务目录

    //服务器IP地址
    public static final String HOST_ADDR = "10.242.142.129";

    //文件云存储服务端口号
    public static final int CLOUD_FILE_SERVICE_PORT = 8889;
}
```

### 文件略缩图预览
    图片小图 url + !imgsmall
    图片中图 url + !imgmiddle
    视频略缩图 url + !videothumb

### 启动服务
./run.sh

