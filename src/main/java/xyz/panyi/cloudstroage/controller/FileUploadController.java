package xyz.panyi.cloudstroage.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import xyz.panyi.cloudstroage.AppConfiguration;
import xyz.panyi.cloudstroage.Config;
import xyz.panyi.cloudstroage.model.HttpResp;
import xyz.panyi.cloudstroage.model.UploadResult;
import xyz.panyi.cloudstroage.util.LogUtil;

import java.io.File;
import java.io.IOException;


/**
 * 文件上传服务
 */
@RestController
public class FileUploadController {
    @Autowired
    private AppConfiguration mAppConfig;

    @ResponseBody
    @PostMapping("/uploadfile")
    public HttpResp uploadFile(@RequestParam("file")MultipartFile multipartFile){
        if(multipartFile.isEmpty()){
            return HttpResp.genError("file is empty");
        }

        String filename = multipartFile.getOriginalFilename();
        String dir = mAppConfig.getFileRootDir();
        File uploadFile = new File(dir , createFileName(filename));
        try {
            multipartFile.transferTo(uploadFile);
        } catch (IOException e) {
            LogUtil.log("文件上传失败 {}" , e);
            return HttpResp.genError(e.getMessage());
        }
        UploadResult result = new UploadResult();
        result.setFilesize((int)uploadFile.length());
        result.setUrl(String.format("http://%s:%d/%s" , host() ,
                Config.CLOUD_FILE_SERVICE_PORT,uploadFile.getName()));
        return  HttpResp.genResp(result);
    }

    public String host(){
        return Config.HOST_ADDR;
    }

    public static String createFileName(String filename){
        return String.format("%s_%s" ,System.currentTimeMillis(), filename);
    }
}
