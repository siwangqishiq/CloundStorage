package xyz.panyi.cloudstroage.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.util.StringUtils;
import xyz.panyi.cloudstroage.util.ImageUtil;
import xyz.panyi.cloudstroage.util.LogUtil;

import javax.activation.MimetypesFileTypeMap;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 文件服务Handler
 */
@ChannelHandler.Sharable
public class FileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static final String FILE_HANDLER_DIV = "!";
    private static final String IMAGE_SMALL = "imgsmall";
    private static final String IMAGE_MIDDLE = "imgmiddle";
    private static final String VIDEO_THUMB = "videothumb";

    private static final int VIDEO_THUMB_WIDTH = 512;

    private String mRoot;

    private static Map<String , Integer> IMAGE_HANDLE_SIZE = new HashMap<String , Integer>();
    static {
        IMAGE_HANDLE_SIZE.put(IMAGE_SMALL , 128);
        IMAGE_HANDLE_SIZE.put(IMAGE_MIDDLE , 256);
    }

    public FileServerHandler(String root){
        this.mRoot = root;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        // 解码失败 400
        if (!request.decoderResult().isSuccess()) {
            LogUtil.log("{} 解析失败" , request.uri());
            sendError(ctx, HttpResponseStatus.BAD_REQUEST);
            return;
        }

        //
        final String uri = request.uri();
        // 处理Uri地址
        String path = sanitizeUri(uri);
        LogUtil.log("uri : " + uri +"   path : "
                + path + " request method : " + request.method().name());

        if (path == null) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }
        //解析
        path = parsePath(path);

        File file = new File(mRoot , path);
        // 如果文件不存在，或不可访问
        if (!file.exists()) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        if (!file.isFile()) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        RandomAccessFile randomAccessFile = null;
        try {
            randomAccessFile = new RandomAccessFile(file, "r");// 以只读的方式打开文件
        } catch (FileNotFoundException fnfe) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        long fileLength = randomAccessFile.length();
        // 创建一个默认的Http响应
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        // 设置响应文件大小
        HttpUtil.setContentLength(response, fileLength);
        // 设置 content Type
        setContentTypeHeader(response, file);
        // 设置 keep alive
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }
        ctx.write(response);

        //通过Netty的ChunkedFile对象直接将文件写入发送到缓冲区中
        ChannelFuture sendFileFuture = ctx.write(new ChunkedFile(randomAccessFile, 0, fileLength, 8 * 1024),
                ctx.newProgressivePromise());
//        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
//            @Override
//            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
//                if (total < 0) {
//                    LogUtil.log("Transfer progress: " + progress);
//                } else {
//                    LogUtil.log("Transfer progress: " + progress + " / " + total);
//                }
//            }
//
//            @Override
//            public void operationComplete(ChannelProgressiveFuture future) throws Exception {
//                LogUtil.log("Transfer complete.");
//            }
//        });
        ChannelFuture lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        //如果不支持keep-Alive，服务器端主动关闭请求
        if (!HttpUtil.isKeepAlive(request)) {
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status,
                Unpooled.copiedBuffer("Failure" + status.toString() + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }

    /**
     * 格式化uri并且获取路径
     *
     * @param uri
     * @return
     */
    private String sanitizeUri(String uri) {
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            try {
                uri = URLDecoder.decode(uri, "ISO-8859-1");
            } catch (UnsupportedEncodingException e1) {
                throw new Error();
            }
        }

        if (!uri.startsWith("/")) {
            return null;
        }

        // uri = uri.replace('/', File.separatorChar);
        if (uri.contains(File.separator + '.')
                || uri.contains('.' + File.separator)
                || uri.startsWith(".")
                || uri.endsWith(".") || INSECURE_URI.matcher(uri).matches()) {

            return null;
        }
        //return System.getProperty("user.dir") + File.separator + uri;
        return uri;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (ctx.channel().isActive()) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    //解析路径  依据参数 决定是否动态生成资源文件
    private String parsePath(String path){
        if(path.startsWith("/") || path.startsWith("\\")){
            path = path.substring(1);
        }

        String pathString = path;
        if(path.endsWith(FILE_HANDLER_DIV + IMAGE_SMALL)){//请求原始文件的小号图片
            pathString = path.substring(0 , path.indexOf(FILE_HANDLER_DIV));
            return imageFileEnsureCreate(pathString , IMAGE_SMALL);
        }else if(path.endsWith(FILE_HANDLER_DIV + IMAGE_MIDDLE)){//请求原始图片的中等文件
            pathString = path.substring(0 , path.indexOf(FILE_HANDLER_DIV));
            return imageFileEnsureCreate(pathString , IMAGE_MIDDLE);
        }else if(path.endsWith(FILE_HANDLER_DIV + VIDEO_THUMB)){//请求视频文件略缩图
            pathString = path.substring(0 , path.indexOf(FILE_HANDLER_DIV));
            return videoThumbEnsureCreate(pathString);
        }

        return path;
    }

    private String imageFileEnsureCreate(final String originPath , String handleName){
        String[] results = parsePaths(originPath);
        String newPath = String.format("%s_%s.%s" , results[0] , handleName , results[1]);
        LogUtil.log("file real path:{}" , newPath);

        final File file = new File(mRoot , newPath);
        if(!file.exists()){//文件已存在
            final int size = IMAGE_HANDLE_SIZE.get(handleName) == null?128:IMAGE_HANDLE_SIZE.get(handleName).intValue();
            genImageThumb(originPath , newPath ,results[1], size);
        }
        return newPath;
    }

    /**
     * 从原始图片 生成略缩图文件
     *
     * @param originPath
     * @param newPath
     * @param dstSize
     */
    private void genImageThumb(final String originPath ,String newPath,String suffix , int dstSize){
        final File file = new File(mRoot , originPath);
        if(!file.exists()){
            LogUtil.log("{} file not exist");
            return;
        }

        try{
            BufferedImage originImage = ImageIO.read(file);

            float originWidth = originImage.getWidth();
            float originHeight = originImage.getHeight();

            //解析meta 数据 读出exif
            ImageUtil.ImageInformation imageInformation = ImageUtil.readImageInformation(file);
            if(imageInformation != null){
                BufferedImage oImage = ImageUtil.transformImage(originImage , ImageUtil.getExifTransformation(imageInformation));
                originImage = oImage;
                originWidth = oImage.getWidth();
                originHeight = oImage.getHeight();
            }

            int dstHeight = 0;
            int dstWidth = 0;
            if(originWidth >= originHeight){
                dstWidth = dstSize;
                dstHeight = (int)(dstSize / (originWidth / originHeight));
            }else{
                dstHeight = dstSize;
                dstWidth = (int)((float)(originWidth / originHeight) * dstSize);
            }


            BufferedImage scaleImage = new BufferedImage(dstWidth , dstHeight , originImage.getType());
            // Graphics canvas = scaleImage.getGraphics();
            Graphics2D graphics2D = scaleImage.createGraphics();
            graphics2D.drawImage(originImage , 0 , 0 ,dstWidth , dstHeight , null);
            graphics2D.dispose();

            if(suffix.equalsIgnoreCase("jfif")){//jfif文件 按jpg处理
                suffix = "jpg";
            }
            boolean processImageResult = ImageIO.write(scaleImage , suffix , new File(mRoot , newPath));
            LogUtil.log("生成图片 {}  {}" ,originPath , processImageResult);

            if(!processImageResult){//生成略缩图失败 直接拷贝原图
                File thumbFile = new File(mRoot,newPath);
                if(thumbFile.exists()){
                    thumbFile.delete();
                }

                Files.copy(Paths.get(mRoot+File.separator +originPath) , Paths.get(thumbFile.getAbsolutePath()));
            }
        }catch (Exception e){
            LogUtil.log(e.toString());
            LogUtil.log("genImageThumb {} 发生错误 {}" , originPath , e.getMessage());
        }
    }

    private String videoThumbEnsureCreate(final String originPath){
        String[] results = parsePaths(originPath);
        String thumbPath = String.format("%s_%s.jpg" , results[0] , VIDEO_THUMB);
        LogUtil.log("file real path:{}" , thumbPath);

        final File file = new File(mRoot , thumbPath);
        if(!file.exists()){//文件已存在
            genVideoThumb(originPath , thumbPath, VIDEO_THUMB_WIDTH);
        }
        return thumbPath;
    }

    private void genVideoThumb(String originPath , String thumbPath , int thumbWidth){
        FFmpegFrameGrabber grabber = null;
        try {
            grabber = FFmpegFrameGrabber.createDefault(new File(mRoot , originPath).getAbsolutePath());
            grabber.start();

            int frameLength = grabber.getLengthInFrames();
            Frame frame;
            int index = 0;
            while(index < frameLength){
                frame = grabber.grabFrame();
                if(index >= 5 && frame != null && frame.image != null){
                    doExecuteFrame(frame , thumbPath);
                    break;
                }
                index++;
            }//end while
        } catch (FFmpegFrameGrabber.Exception e) {
            LogUtil.log("生成{}视频略缩图错误:{}" ,originPath , e);
        } catch (FrameGrabber.Exception e) {
            LogUtil.log("生成{}视频略缩图错误:{}" ,originPath , e);
        } finally {
            if(grabber != null){
                try {
                    grabber.close();
                } catch (FrameGrabber.Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doExecuteFrame(Frame frame , String targetFilePath){
        if(frame == null || frame.image == null){
            return;
        }

        final Java2DFrameConverter converter = new Java2DFrameConverter();
        BufferedImage img = converter.convert(frame);

        try {
            ImageIO.write(img , "jpg" , new File(mRoot , targetFilePath));
        } catch (IOException e) {
            LogUtil.log("生成视频略缩图错误:{}", e);
        }
    }

    public static String[] parsePaths(final String path){
        final String[] result = new String[]{"",""};
        if(StringUtils.isEmpty(path)){
            return result;
        }

        String str = path;
        if(path.indexOf("?") != -1){
            str = path.substring(0 , path.indexOf("?"));
        }

        String[] strs = str.split("/");
        if(strs.length > 0){
            str = strs[strs.length - 1];
            final int lastSeparator = str.lastIndexOf("/");
            if(str.indexOf(".") == -1){
                String name = str.substring(lastSeparator + 1);
                result[0] = str;
                result[1] = "";
            }else{
                String name = str.substring(lastSeparator + 1 , str.indexOf("."));
                String extension = str.substring(str.indexOf(".") + 1);

                result[0] = name;
                result[1] = extension;
            }
        }
        return result;
    }

    private static void handleExifOrientation(final int orientation , Graphics2D canvas){

    }
}//end class
