package com.example.ftpclient.control;

import android.os.Environment;
import android.os.Message;

import com.example.ftpclient.MainActivity;
import com.example.ftpclient.MyApplication;
import com.example.ftpclient.data.FTPFile;
import com.example.ftpclient.exception.DownloadException;
import com.example.ftpclient.exception.ModeFailure;
import com.example.ftpclient.exception.ServerNotFound;
import com.example.ftpclient.exception.SocketError;
import com.example.ftpclient.exception.TypeFailure;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

public class MyClient{
    private MainActivity activity;

    private Socket controlSocket = null;// 控制连接
    private BufferedWriter clientWriter;
    private BufferedReader clientReader;

    private boolean isConnect;//判断是否已经连接到服务端并登录
    private boolean isLoading = false;//判断是否正在上传下载
    private volatile boolean passive=true;//记录主动模式还是被动模式，默认被动模式
    private volatile boolean ascii = false;//设置type为ascii还是binary，默认binary
    public enum Mode{
        Stream,Block,Compressed
    }
    private volatile Mode mode = Mode.Stream;//设置传输模式,默认stream
    public enum Structure{
        File,Record,Page
    }
    private volatile Structure structure = Structure.File;//设置文件传输格式，默认文件结构

    private volatile Socket dataSocket;//数据连接
    private volatile String serverAddress;//服务器ip地址
    private volatile int serverPort;//服务器数据连接的端口
    private volatile ServerSocket serverSocket;//主动模式需要

    private boolean fast = false;
    private volatile Socket dataSocket2;//数据连接
    private volatile String serverAddress2;//服务器ip地址
    private volatile int serverPort2;//服务器数据连接的端口
    private volatile ServerSocket serverSocket2;//主动模式需要

    private volatile String downloadDirectory = Environment.getExternalStorageDirectory().getAbsolutePath()+"/serverDownload";//设置文件下载目录

    public MyClient(){
        isConnect = false;
    }

    /**
     * 建立控制连接
     *
     * @param address ip地址
     * @param port 端口号
     *
     * @exception ServerNotFound 无法连接到服务器
     */
    public void connect(String address, int port) throws ServerNotFound {
        try {
            this.controlSocket = new Socket(address, port);
            clientReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            clientWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
        }catch (IOException e){
            System.out.println(e.getMessage());
            throw new ServerNotFound("无法连接到服务器:"+address+":"+port);
        }
    }

    /**
     * 建立数据连接
     *
     * @return 是否建立成功
     * @throws SocketError socket错误
     */
    public boolean dataConnect() throws SocketError {
        try {
            if (dataSocket != null){
                dataSocket.close();
            }
        }catch (IOException e){
            e.printStackTrace();
        }

        try {
            if (passive){
                System.out.println(serverAddress+":"+serverPort);
                dataSocket = new Socket(serverAddress, serverPort);
                System.out.println("建立数据连接成功");
            }else {
                dataSocket = serverSocket.accept();
            }
            return true;
        }catch (IOException e){
            ignore();
            System.out.println(e.getMessage());
            throw new SocketError(e.getMessage());
        }
    }

    public boolean dataConnectFast() throws SocketError{
        try {
            if (dataSocket2 != null){
                dataSocket2.close();
            }
        }catch (IOException e){
            e.printStackTrace();
        }

        try {
            if (passive){
                System.out.println(serverAddress2+":"+serverPort2);
                dataSocket2 = new Socket(serverAddress2, serverPort2);
                System.out.println("建立数据连接成功");
            }else {
                dataSocket2 = serverSocket2.accept();
            }
            return true;
        }catch (IOException e){
            ignore();
            System.out.println(e.getMessage());
            throw new SocketError(e.getMessage());
        }
    }

    /**
     * 登录-USER、PASS
     *
     * @param username 用户名
     * @param password 密码
     * @return 是否登录成功
     */
    public synchronized boolean login(String username, String password){
        try {
            writeCommand("USER "+username);
            String response = clientReader.readLine();
            System.out.println(response);
            if (response.startsWith("230")){//登录不需要密码
                setConnect(true);
                return true;
            }else if (response.startsWith("201")){//用户名存在，登录需要密码
                writeCommand("PASS "+password);
                String response_pass = clientReader.readLine();
                System.out.println(response_pass);
                if (response_pass!=null && response_pass.startsWith("230")){//密码正确
                    setConnect(true);
                    return true;
                }
            }
        } catch (IOException e) {
            ignore();
        }
        return false;
    }

    //在控制连接上写入命令
    public void writeCommand(String command) throws IOException {
        if (clientWriter!=null){
            clientWriter.write(command);
            clientWriter.write("\r\n");
            clientWriter.flush();
        }else {
            throw new IOException("请先连接");
        }
    }

    //若发生异常则ignore之前写入的命令
    private void ignore(){
        try {
            if (clientReader!=null){
                while (clientReader.ready()){
                    clientReader.read();
                }
            }
        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * 断开连接-QUIT
     *
     * @throws IOException 断开连接失败
     */
    public void disconnect() throws IOException{
        writeCommand("QUIT");
        String response = clientReader.readLine();
        System.out.println(response);
        if (response != null && response.startsWith("221")){
            isConnect = false;
            controlSocket.close();
            clientReader.close();
            clientWriter.close();
        }else {
            isConnect = false;
            throw new IOException("response error");
        }
    }

    /**
     * 被动模式-PASV
     *
     * @throws ModeFailure 被动模式设置失败
     */
    public synchronized void setPassive() throws ModeFailure {
        try {
            writeCommand("PASV");
            String response = clientReader.readLine();
            if (response!=null && response.startsWith("200")){
                String a = response.substring(response.indexOf(":")+2);
                String[] b = a.split(",");
                if (b.length == 6){
                    serverAddress = b[0]+"."+b[1]+"."+b[2]+"."+b[3];
                    serverPort = Integer.parseInt(b[4])*256 + Integer.parseInt(b[5]);
                    System.out.println(serverAddress+":"+serverPort);
                    passive = true;
                }else {
                    ignore();
                    throw new ModeFailure("response error");
                }
            }else {
                ignore();
                throw new ModeFailure(response);
            }
        }catch (IOException e){
            ignore();
            throw new ModeFailure("set passive mode failure");
        }
    }

    public synchronized void setPassiveFast() throws ModeFailure {
        try {
            writeCommand("PASVFAST");
            String response = clientReader.readLine();
            if (response!=null && response.startsWith("200")){
                String a = response.substring(response.indexOf(":")+2);
                String[] b = a.split(",");
                if (b.length == 6){
                    serverAddress2 = b[0]+"."+b[1]+"."+b[2]+"."+b[3];
                    serverPort2 = Integer.parseInt(b[4])*256 + Integer.parseInt(b[5]);
                    System.out.println(serverAddress2+":"+serverPort2);
                    passive = true;
                    fast = true;
                }else {
                    ignore();
                    throw new ModeFailure("response error");
                }
            }else {
                ignore();
                throw new ModeFailure(response);
            }
        }catch (IOException e){
            ignore();
            throw new ModeFailure("set passive mode failure");
        }
    }

    /**
     *主动模式-PORT
     *
     * @throws ModeFailure 主动模式设置失败
     */
    public synchronized void setActive() throws ModeFailure{
        Random random = new Random();
        boolean open = false;
        int p1,p2,port;

        while (!open){
            //随机端口
            p1 = random.nextInt(256);
            p2 = random.nextInt(256);
            port = p1*256+p2;

            //打开serverSocket监听服务端e't
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(port);
                open = true;

                try {
                    String localAddress = GetIP.getIPAddress(MyApplication.getContext());
//                    for (Enumeration<NetworkInterface> enNetI = NetworkInterface.getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
//                        NetworkInterface netI = enNetI.nextElement();
//                        for (Enumeration<InetAddress> enumIpAddr = netI.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
//                            InetAddress inetAddress = enumIpAddr.nextElement();
//                            if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
//                                localAddress = inetAddress.getHostAddress();
//                            }
//                        }
//                    }
                    String[] a = localAddress.split("\\.");
                    writeCommand("PORT "+a[0]+","+a[1]+","+a[2]+","+a[3]+","+p1+","+p2);
                    String response = clientReader.readLine();
                    if (response!=null && response.startsWith("200")){
                        if (this.serverSocket != null){
                            this.serverSocket.close();
                        }
                        passive = false;
                        this.serverSocket = serverSocket;
                    }else {
                        ignore();
                        throw new ModeFailure(response);
                    }
                }catch (IOException e){
                    ignore();
                    throw new ModeFailure("set active mode failure");
                }
            }catch (IOException e){
                open = false;
            }
        }
    }

    public synchronized void setActiveFast() throws ModeFailure{
        Random random = new Random();
        boolean open = false;
        int p1,p2,port;

        while (!open){
            //随机端口
            p1 = random.nextInt(256);
            p2 = random.nextInt(256);
            port = p1*256+p2;

            //打开serverSocket监听服务端
            ServerSocket serverSocket;
            try {
                serverSocket = new ServerSocket(port);
                open = true;

                try {
                    String localAddress = GetIP.getIPAddress(MyApplication.getContext());
//                    for (Enumeration<NetworkInterface> enNetI = NetworkInterface.getNetworkInterfaces(); enNetI.hasMoreElements(); ) {
//                        NetworkInterface netI = enNetI.nextElement();
//                        for (Enumeration<InetAddress> enumIpAddr = netI.getInetAddresses(); enumIpAddr.hasMoreElements(); ) {
//                            InetAddress inetAddress = enumIpAddr.nextElement();
//                            if (inetAddress instanceof Inet4Address && !inetAddress.isLoopbackAddress()) {
//                                localAddress = inetAddress.getHostAddress();
//                            }
//                        }
//                    }
                    String[] a = localAddress.split("\\.");
                    writeCommand("PORTFAST "+a[0]+","+a[1]+","+a[2]+","+a[3]+","+p1+","+p2);
                    String response = clientReader.readLine();
                    if (response!=null && response.startsWith("200")){
                        if (this.serverSocket2 != null){
                            this.serverSocket2.close();
                        }
                        passive = false;
                        fast = true;
                        this.serverSocket2 = serverSocket;
                    }else {
                        ignore();
                        throw new ModeFailure(response);
                    }
                }catch (IOException e){
                    ignore();
                    throw new ModeFailure("set active mode failure");
                }
            }catch (IOException e){
                open = false;
            }
        }
    }

    /**
     *设置ASCII/Binary模式-TYPE
     *
     * @param type A/B
     * @exception TypeFailure 设置type失败
     */
    public synchronized void setType(String type) throws TypeFailure {
        if (type.equals("A") || type.equals("B")){
            try{
                writeCommand("TYPE "+type);
                String response = clientReader.readLine();
                System.out.println(response);
                if (response!=null && response.startsWith("200")){
                    ascii= type.equals("A");
                }else {
                    ignore();
                    throw new TypeFailure(response);
                }
            }catch (IOException e){
                ignore();
                throw new TypeFailure("set type failure");
            }
        }
    }

    /**
     * 设置传输模式-MODE
     *
     * @param mode S/B/C——目前只实现流模式
     * @throws ModeFailure 设置模式失败
     */
    public synchronized void setTransferMode(String mode) throws ModeFailure {
        if (mode.equals("S") || mode.equals("B") || mode.equals("C")){
            try{
                writeCommand("MODE "+mode);
                String response = clientReader.readLine();
                System.out.println(response);
                if (response!=null && response.startsWith("200")){
                    switch (mode){
                        case "S":this.mode = Mode.Stream;break;
                        case "B":this.mode = Mode.Block;break;
                        case "C":this.mode = Mode.Compressed;break;
                    }
                }else {
                    ignore();
                    throw new ModeFailure(response);
                }
            }catch (IOException e){
                ignore();
                throw new ModeFailure("set transfer type failure");
            }
        }
    }

    /**
     * 设置文件传输结构-STRU
     *
     * @param structure F/R/P——目前只实现文件结构
     * @throws ModeFailure 设置文件传输结构失败
     */
    public synchronized void setStructure(String structure) throws ModeFailure {
        if (structure.equals("F") || structure.equals("R") || structure.equals("P")){
            try{
                writeCommand("STRU "+structure);
                String response = clientReader.readLine();
                System.out.println(response);
                if (response!=null && response.startsWith("200")){
                    switch (structure){
                        case "F":this.structure = Structure.File;break;
                        case "R":this.structure = Structure.Record;break;
                        case "P":this.structure = Structure.Page;break;
                    }
                }else {
                    ignore();
                    throw new ModeFailure(response);
                }
            }catch (IOException e){
                ignore();
                throw new ModeFailure("set structure failure");
            }
        }
    }

    /**
     * 列出服务端pathname下文件(directory+file)-SHOW
     *
     * @param pathname 服务端文件路径
     * @return pathname下所有文件信息
     * @throws SocketError socket错误
     */
    public synchronized List<FTPFile> list(String pathname) throws SocketError,DownloadException {
        List<FTPFile> fileList = new ArrayList<>();
        try {
            writeCommand("SHOW "+pathname);
            String response = clientReader.readLine();
            System.out.println(response);
            if (response!=null && response.startsWith("200")){
                //循环读取控制连接上传来的文件信息
                while (true) {
                    String fileInfo;
                    try {
                        fileInfo = clientReader.readLine();
                        if (fileInfo == null || fileInfo.length() == 0) {
                            break;
                        }
                        String[] a = fileInfo.split(",");
                        if (a.length == 2){
                            FTPFile ftpFile;
                            if (a[1].equals("F")){
                                ftpFile = new FTPFile(a[0],false);
                            }else {
                                ftpFile = new FTPFile(a[0],true);
                            }
                            fileList.add(ftpFile);
                        }else {
                            ignore();
                            throw new DownloadException(response);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }else {
                ignore();
                throw new SocketError(response);
            }
        }catch (IOException e){
            ignore();
            throw new SocketError(e.getMessage());
        }
        return fileList;
    }

    /**
     * 列出服务端pathname下所有文件-LIST
     *
     * @param pathname 服务端文件路径
     * @return pathname下所有文件信息
     * @throws SocketError socket错误
     */
    public synchronized List<String> listFiles(String pathname) throws SocketError {
        List<String> fileList = new ArrayList<>();
        try {
            writeCommand("LIST "+pathname);
            String response = clientReader.readLine();
            System.out.println(response);
            if (response!=null && response.startsWith("200")){
                //循环读取控制连接上传来的文件信息
                while (true) {
                    String fileInfo;
                    try {
                        fileInfo = clientReader.readLine();
                        if (fileInfo == null || fileInfo.length() == 0) {
                            break;
                        }
                        fileList.add(fileInfo);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }else {
                ignore();
                throw new SocketError(response);
            }
        }catch (IOException e){
            ignore();
            throw new SocketError(e.getMessage());
        }
        return fileList;
    }

    /**
     * 从服务端下载单个文件-RETR
     *
     * @param pathname 文件名
     * @throws DownloadException 下载失败
     */
    public synchronized void retrieveFile(String pathname) throws DownloadException, SocketError {
        String filename = pathname.substring(pathname.lastIndexOf("/"));
        String destPath = downloadDirectory+"/"+filename;

        try {
            if (fast){
                writeCommand("RETRFAST "+pathname);
            }else {
                writeCommand("RETR "+pathname);
            }
            String response = clientReader.readLine();
            System.out.println(response);
            if (response!=null && response.startsWith("150")){
               //建立数据连接
                boolean dataConnect = dataConnect();
                if (dataConnect){
                    String response_data = clientReader.readLine();
                    if (response_data.startsWith("125")){
                        File file = new File(destPath);
                        if(file.getParentFile()== null || !file.getParentFile().exists()){
                            file.mkdirs();
                        }
                        if (file.exists()) {
                            file.delete();
                            file.createNewFile();
                        }
                        if (!file.exists()){
                            file.createNewFile();
                            System.out.println("文件建立成功");
                        }

                        if (fast){
                            boolean dataConnect2 = dataConnectFast();//建立第二个数据连接
                            if (dataConnect2){
                                String response_data2 = clientReader.readLine();
                                if (response_data2.startsWith("125")){
                                    File fileFirst = new File(downloadDirectory+"/First");
                                    File fileSecond = new File(downloadDirectory+"/Second");
                                    if (fileFirst.exists()){
                                        fileFirst.delete();
                                    }
                                    if (fileSecond.exists()){
                                        fileSecond.delete();
                                    }
                                    fileFirst.createNewFile();
                                    fileSecond.createNewFile();
                                    Thread t;
                                    if (ascii){//ASCII模式
                                        t = new Thread(() -> {
                                            try {
                                                BufferedReader br = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                                                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileFirst)));
                                                String s;
                                                int row = 0;
                                                while ((s = br.readLine()) != null) {
                                                    row++;
                                                    if (row != 1) {
                                                        bw.write("\r\n");
                                                    }
                                                    bw.write(s);
                                                    bw.flush();
                                                }
                                                br.close();
                                                bw.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        });
                                        t.start();

                                        BufferedReader br2 = new BufferedReader(new InputStreamReader(dataSocket2.getInputStream()));
                                        BufferedWriter bw2 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileSecond)));

                                        String s;
                                        while ((s = br2.readLine()) != null) {
                                            bw2.write("\r\n");
                                            bw2.write(s);
                                            bw2.flush();
                                        }

                                        br2.close();
                                        bw2.close();
                                        t.join();

                                        //将两个文件的内容存入一个文件
                                        BufferedReader br3 = new BufferedReader(new InputStreamReader(new FileInputStream(fileFirst)));
                                        BufferedReader br4 = new BufferedReader(new InputStreamReader(new FileInputStream(fileSecond)));
                                        BufferedWriter bw4 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));

                                        while((s = br3.readLine()) != null){
                                            bw4.write(s);
                                            bw4.flush();
                                        }
                                        while ((s = br4.readLine())!= null){
                                            bw4.write(s);
                                            bw4.flush();
                                        }

                                        br3.close();
                                        br4.close();
                                        bw4.close();
                                    }
                                    else {//binary模式
                                        t = new Thread(() -> {
                                            try {
                                                OutputStream os = new FileOutputStream(fileFirst);
                                                InputStream input = dataSocket.getInputStream();
                                                byte[] buf = new byte[1024 * 1024];
                                                int b;
                                                while ((b = input.read(buf)) != -1) {
                                                    if (b == 1024 * 1024) {
                                                        os.write(buf);
                                                    } else {
                                                        byte[] temp = new byte[b];
                                                        System.arraycopy(buf, 0, temp, 0, b);
                                                        os.write(temp);
                                                    }
                                                    os.flush();
                                                    buf = new byte[1024 * 1024];
                                                }
                                                os.close();
                                                input.close();
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        });
                                        t.start();

                                        OutputStream os2 = new FileOutputStream(fileSecond);
                                        InputStream input2 = dataSocket2.getInputStream();

                                        byte[] buf = new byte[1024 * 1024];
                                        int b;
                                        while ((b = input2.read(buf)) != -1) {
                                            if(b == 1024 * 1024) {
                                                os2.write(buf);
                                            }else{
                                                byte[] temp = new byte[b];
                                                System.arraycopy(buf, 0, temp, 0, b);
                                                os2.write(temp);
                                            }
                                            os2.flush();
                                            buf = new byte[1024 * 1024];
                                        }
                                        os2.close();
                                        input2.close();

                                        t.join();

                                        //将两个文件合并为一个文件
                                        InputStream input3 = new FileInputStream(fileFirst);
                                        InputStream input4 = new FileInputStream(fileSecond);
                                        OutputStream output = new FileOutputStream(file);

                                        while ((b = input3.read(buf)) != -1) {
                                            if (b == 1024 * 1024) {
                                                output.write(buf);
                                                output.flush();
                                            } else {
                                                byte[] temp = new byte[b];
                                                System.arraycopy(buf, 0, temp, 0, b);
                                                output.write(temp);
                                            }
                                            output.flush();
                                            buf = new byte[1024 * 1024];
                                        }
                                        while ((b = input4.read(buf)) != -1) {
                                            if (b == 1024 * 1024) {
                                                output.write(buf);
                                                output.flush();
                                            } else {
                                                byte[] temp = new byte[b];
                                                System.arraycopy(buf, 0, temp, 0, b);
                                                output.write(temp);
                                            }
                                            output.flush();
                                            buf = new byte[1024 * 1024];
                                        }

                                        input3.close();
                                        input4.close();
                                        output.close();
                                    }
                                    fileFirst.delete();
                                    fileSecond.delete();
                                }else {
                                    ignore();
                                    throw new SocketError(response_data2);
                                }
                            }else {
                                ignore();
                                this.isLoading = false;
                                throw new SocketError("建立数据连接失败");
                            }
                        }
                        else {
                            if (ascii) {//ASCII模式
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file)));
                                BufferedReader reader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));

                                String line;
                                int lineNum = 0;
                                long a = System.nanoTime();
                                while ((line = reader.readLine()) != null) {
                                    if (lineNum == 0) {
                                        writer.write(line);
                                        lineNum++;
                                    } else {
                                        writer.write("\r\n");
                                        writer.write(line);
                                    }
                                    writer.flush();
                                    long b = System.nanoTime();
                                    if (b - a > 2000000000) {
                                        sendMessage("下载中...已下载"+((b-a)/1000000000)+"s");
                                    }
                                }

                                writer.close();
                                reader.close();
                            }
                            else {//Binary模式
                                OutputStream writer = new FileOutputStream(file);
                                InputStream reader = dataSocket.getInputStream();

                                //每次最多读取1MB
                                byte[] buf = new byte[1024 * 1024];
                                int readNum;

                                long a = System.nanoTime();
                                while ((readNum = reader.read(buf)) != -1) {
                                    if (readNum == 1024 * 1024) {
                                        writer.write(buf);
                                        writer.flush();
                                    } else {
                                        byte[] temp = new byte[readNum];
                                        System.arraycopy(buf, 0, temp, 0, readNum);
                                        writer.write(temp);
                                        writer.flush();
                                    }
                                    buf = new byte[1024 * 1024];
                                    long b = System.nanoTime();
                                    if (b - a > 2000000000) {
                                        sendMessage("下载中...已下载"+((b-a)/1000000000)+"s");
                                    }
                                }

                                //关闭文件输出流
                                writer.close();
                                reader.close();
                            }
                        }

                        System.out.println("md5:"+FtpUtil.md5HashCode(destPath));
                        System.out.println(file.length());

                        String response_connect = clientReader.readLine();
                        if (response_connect!=null && !response_connect.startsWith("226")){
                            ignore();
                            throw new SocketError("关闭数据连接失败");
                        }

                        dataSocket.close();
                        dataSocket = null;
                        if (dataSocket2 != null){
                            dataSocket2.close();
                            dataSocket2 = null;
                        }

                        String response_file = clientReader.readLine();
                        if (response_file!=null && !response_file.startsWith("250")){
                            ignore();
                            throw new SocketError("文件操作异常");
                        }
                    }else {
                        ignore();
                        throw new SocketError(response_data);
                    }
                }else {
                    ignore();
                    throw new SocketError("建立数据连接失败");
                }
            }else {
                ignore();
                throw new SocketError(response);
            }
        }catch (IOException | InterruptedException e){
            ignore();
            this.isLoading = false;
            throw new DownloadException(e.getMessage());
        }
    }

    /**
     * 上传本地文件到服务端-STOR
     *
     * @param pathname 文件名
     * @throws DownloadException 上传失败
     */
    public synchronized void storageFile(String pathname) throws DownloadException, SocketError {
        String filename = pathname.substring(pathname.lastIndexOf("/"));
        String parentPath = pathname.substring(0, pathname.lastIndexOf("/"));
        try {
            if (fast){
                writeCommand("STORFAST "+filename);
            }else {
                writeCommand("STOR "+filename);
            }
            String response = clientReader.readLine();
            System.out.println(response);
            if (response!=null && response.startsWith("150")){
                //建立数据连接
                boolean dataConnect = dataConnect();
                if (dataConnect){
                    String response_data = clientReader.readLine();
                    if (response_data.startsWith("125")){
                        if (fast){
                            boolean dataConnect2 = dataConnectFast();//建立第二个数据连接
                            if (dataConnect2){
                                String response_data2 = clientReader.readLine();
                                if (response_data2.startsWith("125")){
                                    File file = new File(pathname);
                                    long middleSize = file.length()/2;
                                    File fileFirst = new File(parentPath+"/First");
                                    File fileSecond = new File(parentPath+"/Second");
                                    if (fileFirst.exists()){
                                        fileFirst.delete();
                                    }
                                    if (fileSecond.exists()){
                                        fileSecond.delete();
                                    }
                                    fileFirst.createNewFile();
                                    fileSecond.createNewFile();
                                    if (ascii){//ASCII模式
                                        long currentSize = 0;
                                        //将原文件分两半写入两个新文件
                                        BufferedReader br0 = new BufferedReader(new FileReader(file));
                                        BufferedWriter bw01 = new BufferedWriter(new FileWriter(fileFirst));
                                        BufferedWriter bw02 = new BufferedWriter(new FileWriter(fileSecond));

                                        String s0;
                                        while ((s0 = br0.readLine()) != null) {
                                            currentSize += s0.length();
                                            if(currentSize <= middleSize){
                                                bw01.write(s0);
                                                bw01.write("\r\n");
                                                bw01.flush();
                                            } else{
                                                bw02.write(s0);
                                                bw02.write("\r\n");
                                                bw02.flush();
                                            }
                                        }

                                        br0.close();
                                        bw01.close();
                                        bw02.close();

                                        //写入dataSocket
                                        Thread t = new Thread(()->{
                                            try {
                                                BufferedReader br2 = new BufferedReader(new FileReader(fileSecond));
                                                BufferedWriter bw2 = new BufferedWriter(new OutputStreamWriter(dataSocket2.getOutputStream()));
                                                String s2;
                                                while ((s2 = br2.readLine()) != null) {
                                                    bw2.write(s2);
                                                    bw2.write("\r\n");
                                                    bw2.flush();
                                                }
                                                br2.close();
                                                bw2.close();
                                                fileSecond.delete();
                                            }catch (IOException e){
                                                e.printStackTrace();
                                            }
                                        });
                                        t.start();

                                        BufferedReader br = new BufferedReader(new FileReader(fileFirst));
                                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream()));

                                        String s1;
                                        while ((s1 = br.readLine()) != null) {
                                            bw.write(s1);
                                            bw.write("\r\n");
                                            bw.flush();
                                        }

                                        br.close();
                                        bw.close();
                                        fileFirst.delete();

                                        t.join();
                                    }
                                    else{//binary模式
                                        byte[] buf0 = new byte[1024*1024];
                                        byte[] buf1 = new byte[1024*1024];

                                        InputStream input0 = new FileInputStream(file);
                                        OutputStream os01 = new FileOutputStream(fileFirst);
                                        OutputStream os02 = new FileOutputStream(fileSecond);

                                        int b1;
                                        long currentSize = 0;
                                        while ( (b1 = input0.read(buf0)) != -1) {
                                            currentSize += b1;
                                            if(b1 == 1024 * 1024) {
                                                os01.write(buf0);
                                            }else{
                                                byte[] temp = new byte[b1];
                                                System.arraycopy(buf0, 0, temp, 0, b1);
                                                os01.write(temp);
                                            }
                                            os01.flush();
                                            if (currentSize >= middleSize){
                                                break;
                                            }
                                        }
                                        while ( (b1 = input0.read(buf1)) != -1) {
                                            if(b1 == 1024 * 1024) {
                                                os02.write(buf1);
                                            }else{
                                                byte[] temp = new byte[b1];
                                                System.arraycopy(buf1, 0, temp, 0, b1);
                                                os02.write(temp);
                                            }
                                            os02.flush();
                                        }

                                        //写入dataSocket
                                        Thread t = new Thread(() -> {
                                            try {
                                                OutputStream os = dataSocket.getOutputStream();
                                                InputStream input = new FileInputStream(fileFirst);
                                                byte[] buf = new byte[1024*1024];
                                                int b;
                                                while ( (b = input.read(buf)) != -1) {
                                                    if(b == 1024 * 1024) {
                                                        os.write(buf);
                                                        os.flush();
                                                    }else{
                                                        byte[] temp = new byte[b];
                                                        System.arraycopy(buf, 0, temp, 0, b);
                                                        os.write(temp);
                                                        os.flush();
                                                    }
                                                }
                                                os.close();
                                                input.close();
                                                fileFirst.delete();
                                            }catch (IOException e){
                                                e.printStackTrace();
                                            }
                                        });
                                        t.start();

                                        OutputStream os2 = dataSocket2.getOutputStream();
                                        InputStream input2 = new FileInputStream(fileSecond);

                                        byte[] buf = new byte[1024*1024];
                                        int b2;
                                        while ( (b2 = input2.read(buf)) != -1) {
                                            if(b2 == 1024 * 1024) {
                                                os2.write(buf);
                                            }else{
                                                byte[] temp = new byte[b2];
                                                System.arraycopy(buf, 0, temp, 0, b2);
                                                os2.write(temp);
                                            }
                                            os2.flush();
                                        }
                                        os2.close();
                                        input2.close();
                                        fileSecond.delete();

                                        t.join();
                                    }
                                }else {
                                    ignore();
                                    throw new SocketError(response_data2);
                                }
                            }else {
                                ignore();
                                this.isLoading = false;
                                throw new SocketError("建立数据连接失败");
                            }
                        }else {
                            if (ascii) {//ASCII模式
                                File file = new File(pathname);
                                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream()));
                                BufferedReader reader = new BufferedReader(new FileReader(file));

                                String line;

                                long a = System.nanoTime();
                                while ((line = reader.readLine()) != null) {
                                    System.out.println(line);
                                    writer.write(line);
                                    writer.write("\r\n");
                                    writer.flush();
                                    long b = System.nanoTime();
                                    if (b - a > 1000000000) {
                                        sendMessage("上传中...已上传" + ((b - a) / 1000000000) + "s");
                                    }
                                }

                                writer.close();
                                reader.close();
                            } else {//Binary模式
                                OutputStream writer = dataSocket.getOutputStream();
                                InputStream reader = new FileInputStream(pathname);

                                //每次最多读取1MB
                                byte[] buf = new byte[1024 * 1024];
                                int readNum;

                                long a = System.nanoTime();
                                while ((readNum = reader.read(buf)) != -1) {
                                    if (readNum == 1024 * 1024) {
                                        writer.write(buf);
                                        writer.flush();
                                    } else {
                                        byte[] temp = new byte[readNum];
                                        System.arraycopy(buf, 0, temp, 0, readNum);
                                        writer.write(temp);
                                        writer.flush();
                                    }
                                    buf = new byte[1024 * 1024];
                                    long b = System.nanoTime();
                                    if (b - a > 1000000000) {
                                        sendMessage("上传中...已上传" + ((b - a) / 1000000000) + "s");
                                    }
                                }

                                //关闭文件输出流
                                writer.close();
                                reader.close();
                            }
                        }

                        System.out.println("md5:"+FtpUtil.md5HashCode(pathname));
                        System.out.println(new File(pathname).length());

                        String response_connect = clientReader.readLine();
                        if (response_connect!=null && !response_connect.startsWith("226")){
                            ignore();
                            throw new SocketError("关闭数据连接失败");
                        }

                        dataSocket.close();
                        dataSocket = null;
                        if (dataSocket2 != null){
                            dataSocket2.close();
                            dataSocket2 = null;
                        }

                        String response_file = clientReader.readLine();
                        if (response_file!=null && !response_file.startsWith("250")){
                            ignore();
                            throw new SocketError("文件操作异常");
                        }
                    }else {
                        ignore();
                        throw new SocketError(response_data);
                    }
                }else {
                    ignore();
                    this.isLoading = false;
                    throw new SocketError("建立数据连接失败");
                }
            }else {
                ignore();
                throw new SocketError(response);
            }
        }catch (IOException | InterruptedException e){
            ignore();
            throw new DownloadException(e.getMessage());
        }
    }

    public synchronized void noop() throws IOException {
        try {
            writeCommand("NOOP");
            String response = clientReader.readLine();
            if (response != null && response.startsWith("200")){
                System.out.println("success");
            }else {
                System.out.println(response);
                throw new IOException(response);
            }
        }catch (IOException e){
            throw new IOException("noop failure");
        }
    }

    public void sendMessage(String message){
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = message;
        activity.getmHandler().sendMessage(msg);
    }

    //返回是否已登录
    public boolean isConnect() {
        return isConnect;
    }

    public void setConnect(boolean connect) {
        isConnect = connect;
    }

    //设置下载目录
    public void setDownloadDirectory(String downloadDirectory) throws DownloadException{
        File file = new File(downloadDirectory);
        if (!file.exists()){
            boolean f = file.mkdir();
            if (!f){
                throw new DownloadException("创建目录失败，请重新选择目录");
            }
        }
        if (!file.isDirectory()){
            throw new DownloadException("当前路径不是一个目录");
        }
        this.downloadDirectory = downloadDirectory;
    }

    public String getDownloadDirectory(){
        return downloadDirectory;
    }

    public boolean isPassive() {
        return passive;
    }

    public boolean isAscii() {
        return ascii;
    }

    public Mode getMode() {
        return mode;
    }

    public Structure getStructure() {
        return structure;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }

    public void setActivity(MainActivity activity) {
        this.activity = activity;
    }

    public boolean isFast() {
        return fast;
    }

    public void setFast(boolean fast) {
        this.fast = fast;
    }
}
