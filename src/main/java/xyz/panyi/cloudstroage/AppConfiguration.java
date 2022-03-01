package xyz.panyi.cloudstroage;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xyz.panyi.cloudstroage.util.LogUtil;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

@Component
public class AppConfiguration {
    private String mFileRootDir = Config.CLOUD_FILE_SERVICE_DIR;

    private static final String INNER_IP = "10.0.4.13";

    public static String INTRANET_IP = getIntranetIp(); // 内网IP

    public String getHostIP() {
        return hostIP;
    }

    public void setHostIP(String hostIP) {
        this.hostIP = hostIP;
    }

    private String hostIP;

    @PostConstruct
    public void init(){
        prepareDir();
        hostIP = getV4IP();
    }


    private void prepareDir(){
        if(mFileRootDir.startsWith("/")
                || mFileRootDir.startsWith("C:")
                || mFileRootDir.startsWith("D:")
                || mFileRootDir.startsWith("E:")){
            //直接使用配置用的
        }else{
            String userHome = System.getProperty("user.dir");
            LogUtil.log("server userHome : " + userHome);
            if(StringUtils.isEmpty(userHome)){
                userHome = "/home/admin";
            }
            mFileRootDir = userHome + File.separator + Config.CLOUD_FILE_SERVICE_DIR;
        }

        //创建目录
        File dir = new File(mFileRootDir);
        if(!dir.exists()){
            dir.mkdirs();
        }
        LogUtil.log("创建目录: {}" , dir.getAbsolutePath());
    }

    public static String getV4IP() {
        String result = null;
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("baidu.com", 80));
            result = socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * 获得内网IP
     * @return 内网IP
     */
    private static String getIntranetIp(){
        try{
            return InetAddress.getLocalHost().getHostAddress();
        } catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    public String getFileRootDir() {
        return mFileRootDir;
    }
}
