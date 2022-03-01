package xyz.panyi.cloudstroage.model;

/**
 * 文件上传结果
 */
public class UploadResult {
    private String url;
    private int filesize;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getFilesize() {
        return filesize;
    }

    public void setFilesize(int filesize) {
        this.filesize = filesize;
    }
}
