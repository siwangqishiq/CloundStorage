package xyz.panyi.cloudstroage.model;

public class HttpResp<T> {
    public static class Codes{
        public static final int SUCCESS = 200;
        public static final int ERROR = 500;
    }

    private int code;
    private String msg;
    private T data;

    public static HttpResp<String> genError(String message){
        HttpResp<String> httpResp = new HttpResp<String>(Codes.ERROR , message);
        return httpResp;
    }

    public static <T> HttpResp<T> genResp(T data){
        HttpResp<T> httpResp = new HttpResp<T>(Codes.SUCCESS );
        httpResp.setData(data);
        return httpResp;
    }

    public HttpResp(int code) {
        this.code = code;
    }

    public HttpResp(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public HttpResp(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}