package com.example.ftpclient;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ftpclient.control.FtpUtil;
import com.example.ftpclient.control.MyClient;
import com.example.ftpclient.data.FTPFile;
import com.example.ftpclient.exception.DownloadException;
import com.example.ftpclient.exception.SocketError;

import java.util.ArrayList;
import java.util.List;

public class ServerFileAdapter extends RecyclerView.Adapter<ServerFileAdapter.ViewHolder> {
    private final List<FTPFile> FtpList = new ArrayList<>();
    private MyClient myClient;
    MainActivity activity;

    static class ViewHolder extends RecyclerView.ViewHolder{
       ImageView File_imageview;
       TextView File_textView;
       public ViewHolder(View v){
           super(v);
           File_imageview=(ImageView) v.findViewById(R.id.file_image);
           File_textView=(TextView) v.findViewById(R.id.file_name);
       }
    }

    public ServerFileAdapter(List<FTPFile> list, MainActivity activity){
        setFtpList(list);
        setMyClient(FtpUtil.getMyClient());
        this.activity = activity;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setFtpList(List<FTPFile> ftpList) {
        FtpList.clear();
        FtpList.addAll(ftpList);
        notifyDataSetChanged();
    }

    public void setMyClient(MyClient client){
        this.myClient = client;
    }

    @Override
    public int getItemCount() {
        return FtpList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.directory_item,parent,false);
        final ViewHolder viewHolder=new ViewHolder(view);

        //点击监听
        view.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onClick(View v) {
               final FTPFile file = FtpList.get(viewHolder.getAdapterPosition());
               if(file!=null && file.isDirectory()) {
                   try {
                       List<FTPFile> list = myClient.list(file.getPath());
                       file.setFileList(list);
                       for (FTPFile f:list){
                           f.setPath(f.getFilename());
                           f.setParentFile(file);
                       }
                       setFtpList(list);
                   } catch (SocketError | DownloadException e) {
                       Toast.makeText(MyApplication.getContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
                   }
               }
            }
        });

        //长摁监听
        view.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                //创建弹出式菜单对象
                PopupMenu popup = new PopupMenu(MyApplication.getContext(), v);
                final MenuInflater inflater = popup.getMenuInflater();//获取菜单填充器
                inflater.inflate(R.menu.server_operation, popup.getMenu());//填充菜单
                //绑定菜单项的点击事件
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @SuppressLint("NotifyDataSetChanged")
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        FTPFile file=FtpList.get(viewHolder.getAdapterPosition());
                        if (item.getItemId() == R.id.download) {
                            if (!FtpUtil.getMyClient().isConnect()){
                                Toast.makeText(MyApplication.getContext(),"请先连接",Toast.LENGTH_SHORT).show();
                            }else if(FtpUtil.getMyClient().isLoading()){
                                Toast.makeText(MyApplication.getContext(),"当前正在下载其它文件，请稍后",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MyApplication.getContext(), "开始下载" + file.getFilename().substring(file.getFilename().lastIndexOf("/") + 1), Toast.LENGTH_SHORT).show();
                                                    item.setChecked(false);
                                                    popup.dismiss();
                                                }
                                            });
                                            FtpUtil.getMyClient().setActivity(activity);
                                            long a = System.nanoTime();
                                            if (file.isDirectory()) {
                                                FtpUtil.downloadDirectory(file.getFilename());
                                            } else {
                                                FtpUtil.downloadFile(file.getFilename());
                                            }
                                            long b = System.nanoTime();
                                            System.out.println("下载时长：" + ((b - a) / 1000000000) + "s");
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MyApplication.getContext(), file.getFilename().substring(file.getFilename().lastIndexOf("/") + 1) + "下载成功,下载时长：" + ((b - a) / 1000000000) + "s", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        } catch (DownloadException e) {
                                            activity.runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MyApplication.getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }
                                    }
                                }).start();
                            }
                        }
                        notifyDataSetChanged();
                        return true;
                    }
                });

                popup.show();
                return true;
            }
        });

        return viewHolder;
    }

    //展示图标及文件名
    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FTPFile ftpFile=FtpList.get(position);
        holder.File_textView.setText(ftpFile.getFilename().substring(ftpFile.getFilename().lastIndexOf("/")+1));
        if(ftpFile.isDirectory()){
            holder.File_imageview.setImageDrawable(MyApplication.getContext().getDrawable(R.drawable.directory));
        }else{
            holder.File_imageview.setImageDrawable(MyApplication.getContext().getDrawable(R.drawable.file));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    public void LastFile(){//切换到父目录
        try {
            List<FTPFile> list = myClient.list("/");
            for (FTPFile file : list) {
                file.setPath(file.getFilename());
            }
            setFtpList(list);
        }catch (SocketError | DownloadException e){
            Toast.makeText(MyApplication.getContext(),e.getMessage(),Toast.LENGTH_SHORT).show();
        }
    }
}
