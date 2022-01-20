package com.example.ftpclient.control;

import com.example.ftpclient.MainActivity;
import com.example.ftpclient.MyApplication;
import com.example.ftpclient.exception.DownloadException;
import com.example.ftpclient.exception.SocketError;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class FtpUtil {
    private static final MyClient myClient = new MyClient();

    public static MyClient getMyClient() {
        return myClient;
    }

    //上传文件到服务端
    public static void uploadFile(String pathname) throws DownloadException {
        try {
            myClient.setLoading(true);
            myClient.storageFile(pathname);
            myClient.setLoading(false);
        }catch (SocketError | DownloadException e){
            myClient.setLoading(false);
            throw new DownloadException(e.getMessage());
        }
    }

    //上传文件夹到服务端
    public static void uploadDirectory(String pathname) throws DownloadException {
        File file = new File(pathname);
        List<String> fileList = new ArrayList<>();
        getFile(file,fileList);

        try {
            myClient.setLoading(true);
            for (String childFile: fileList){
                myClient.storageFile(childFile);
            }
            myClient.setLoading(false);
        }catch (SocketError | DownloadException e){
            myClient.setLoading(false);
            throw new DownloadException(e.getMessage());
        }
    }

    public static void getFile(File file, List<String> fileList){
        File[] files = file.listFiles();
        if(files != null){
            for(File fileItem : files){
                if(fileItem.isDirectory()){
                    getFile(fileItem,fileList);
                }else{
                    fileList.add(fileItem.getPath());
                }
            }
        }
    }

    //从服务端下载文件
    public static void downloadFile(String pathname) throws DownloadException {
        try {
            myClient.setLoading(true);
            myClient.retrieveFile(pathname);
            myClient.setLoading(false);
        }catch (SocketError | DownloadException e){
            myClient.setLoading(false);
            throw new DownloadException(e.getMessage());
        }
    }

    //从服务端下载文件夹
    public static void downloadDirectory(String pathname) throws DownloadException {
        try {
            myClient.setLoading(true);
            List<String> list = myClient.listFiles(pathname);
            for (String s:list){
                myClient.retrieveFile(s);
            }
            myClient.setLoading(false);
        }catch (SocketError | DownloadException e){
            myClient.setLoading(false);
            throw new DownloadException(e.getMessage());
        }
    }

    //md5校验
    public static String md5HashCode(String filePath) {
        try {
            InputStream fis = new FileInputStream(filePath);
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer, 0, 1024)) != -1) {
                md.update(buffer, 0, length);
            }
            fis.close();
            //转换并返回包含16个元素字节数组,返回数值范围为-128到127
            byte[] md5Bytes = md.digest();
            BigInteger bigInt = new BigInteger(1, md5Bytes);
            return bigInt.toString(16);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
