package com.example.ftpclient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StrictMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.example.ftpclient.control.FtpUtil;
import com.example.ftpclient.control.MyClient;
import com.example.ftpclient.data.FTPFile;
import com.example.ftpclient.exception.DownloadException;
import com.example.ftpclient.exception.ModeFailure;
import com.example.ftpclient.exception.SocketError;
import com.example.ftpclient.exception.TypeFailure;
import com.google.android.material.navigation.NavigationView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private DrawerLayout mDrawerLayout;

    private MyClient myClient = FtpUtil.getMyClient();

    private List<File> fileList;
    private final List<FTPFile> ftpList=new ArrayList<>();

    private LocalFileAdapter adapter;
    private ServerFileAdapter FTPAdapter;

    private Toast toast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        getPermissions();
    }

    public void Init(){
        boolean getData = InitData();
        mDrawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        InitUI(getData);
        InitMenu();
        InitBroadCast();
    }

    private void InitUI(boolean getData) {
        ActionBar actionBar = getSupportActionBar();
        //?????????????????????
        if(actionBar!=null){
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.menu);
        }

        if (getData){
            //????????????????????????????????????????????????
            recyclerView=(RecyclerView)findViewById(R.id.file_list);
            LinearLayoutManager layoutManager=new LinearLayoutManager(this);
            recyclerView.setLayoutManager(layoutManager);
            adapter=new LocalFileAdapter(fileList,this);
            recyclerView.setAdapter(adapter);
        }
    }

    //??????Menu???????????????
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main,menu);
        return true;
    }

    //?????????????????????????????????????????????
    @SuppressLint("NonConstantResourceId")
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
            case R.id.setting:
                AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
                builder1.setTitle("????????????");
                //    ??????LayoutInflater???????????????xml???????????????????????????View??????
                View view = LayoutInflater.from(MainActivity.this).inflate(R.layout.setting_download_directory, null);
                //    ?????????????????????????????????????????????????????????Content
                builder1.setView(view);

                EditText downloadDirectory = (EditText)view.findViewById(R.id.downloadDirectory);
                downloadDirectory.setText(myClient.getDownloadDirectory());

                builder1.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String a = downloadDirectory.getText().toString().trim();
                        try {
                            myClient.setDownloadDirectory(a);
                            Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } catch (DownloadException e) {
                            Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                builder1.setNegativeButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
                AlertDialog dialog1 = builder1.create();
                dialog1.show();
                break;
            case R.id.passive:
                final String[] items1 = {"????????????", "????????????"};
                AlertDialog.Builder builder2 = new AlertDialog.Builder(this);
                builder2.setTitle("??????????????????/????????????");

                int checkedItem = 0;
                if (myClient.isPassive()){
                    checkedItem = 1;
                }

                builder2.setSingleChoiceItems(items1, checkedItem,new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int i){
                        if (i == 0){
                            if (myClient.isPassive()){
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setActive();
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }else {
                                Toast.makeText(MainActivity.this,"????????????????????????",Toast.LENGTH_SHORT).show();
                            }
                        }else {
                            if (myClient.isPassive()){
                                Toast.makeText(MainActivity.this,"????????????????????????",Toast.LENGTH_SHORT).show();
                            }else {
                                if (myClient.isConnect()){
                                    try {
                                        myClient.setPassive();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } catch (ModeFailure modeFailure) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        dialog.dismiss();
                                    }
                                }else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    dialog.dismiss();
                                }
                            }
                        }
                    }
                });

                builder2.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog2 = builder2.create();
                dialog2.show();
                break;
            case R.id.type:
                final String[] items2 = {"ASCII", "Binary"};
                AlertDialog.Builder builder3 = new AlertDialog.Builder(this);
                builder3.setTitle("??????TYPE");

                int checkedItem2 = 1;
                if (myClient.isAscii()){
                    checkedItem2 = 0;
                }

                builder3.setSingleChoiceItems(items2, checkedItem2,new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        if (i == 0){
                            if (myClient.isAscii()){
                                Toast.makeText(MainActivity.this,"????????????ASCII??????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setType("A");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (TypeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }else {
                            if (myClient.isAscii()){
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setType("B");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (TypeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }else {
                                Toast.makeText(MainActivity.this,"????????????Binary??????",Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });

                builder3.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog3 = builder3.create();
                dialog3.show();
                break;
            case R.id.mode:
                final String[] items3 = {"Stream", "Block","Compress"};
                AlertDialog.Builder builder4 = new AlertDialog.Builder(this);
                builder4.setTitle("??????MODE");

                int checkedItem3 = 0;
                if (myClient.getMode() == MyClient.Mode.Block){
                    checkedItem3 = 1;
                }else if (myClient.getMode() == MyClient.Mode.Compressed){
                    checkedItem3 = 2;
                }

                builder4.setSingleChoiceItems(items3, checkedItem3,new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        if (i == 0){
                            if (myClient.getMode() == MyClient.Mode.Stream){
                                Toast.makeText(MainActivity.this,"????????????Stream??????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setTransferMode("S");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }else if (i==1){
                            if (myClient.getMode() == MyClient.Mode.Block){
                                Toast.makeText(MainActivity.this,"????????????Block??????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setTransferMode("B");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }else {
                            if (myClient.getMode() == MyClient.Mode.Compressed){
                                Toast.makeText(MainActivity.this,"????????????Compressed??????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setTransferMode("C");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }
                    }
                });

                builder4.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog4 = builder4.create();
                dialog4.show();
                break;
            case R.id.structure:
                final String[] items4 = {"File", "Record","Page"};
                AlertDialog.Builder builder5 = new AlertDialog.Builder(this);
                builder5.setTitle("??????MODE");

                int checkedItem4 = 0;
                if (myClient.getStructure() == MyClient.Structure.Record){
                    checkedItem4 = 1;
                }else if (myClient.getStructure() == MyClient.Structure.Page){
                    checkedItem4 = 2;
                }

                builder5.setSingleChoiceItems(items4, checkedItem4, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        if (i == 0){
                            if (myClient.getStructure() == MyClient.Structure.File){
                                Toast.makeText(MainActivity.this,"????????????File??????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setStructure("F");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }else if (i==1){
                            if (myClient.getStructure() == MyClient.Structure.Record){
                                Toast.makeText(MainActivity.this,"????????????Record??????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setStructure("R");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }else {
                            if (myClient.getStructure() == MyClient.Structure.Page){
                                Toast.makeText(MainActivity.this,"?????????????????????",Toast.LENGTH_SHORT).show();
                            }else {
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            try {
                                                myClient.setStructure("P");
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                            } catch (ModeFailure modeFailure) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                                    }
                                                });
                                                dialog.dismiss();
                                            }
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }
                        }
                    }
                });

                builder5.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog5 = builder5.create();
                dialog5.show();
                break;
            case R.id.fast:
                final String[] items5 = {"????????????", "????????????"};
                AlertDialog.Builder builder6 = new AlertDialog.Builder(this);
                builder6.setTitle("??????????????????/????????????");

                int checkedItem5 = 0;
                if (myClient.isFast()){
                    checkedItem5 = 1;
                }

                builder6.setSingleChoiceItems(items5, checkedItem5,new DialogInterface.OnClickListener(){
                    @Override
                    public void onClick(DialogInterface dialog, int i){
                        if (i == 0){
                            if (myClient.isFast()){
                                new Thread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (myClient.isConnect()){
                                            myClient.setFast(false);
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }else {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                            dialog.dismiss();
                                        }
                                    }
                                }).start();
                            }else {
                                Toast.makeText(MainActivity.this,"????????????????????????",Toast.LENGTH_SHORT).show();
                            }
                        }else {
                            if (myClient.isFast()){
                                Toast.makeText(MainActivity.this,"????????????????????????",Toast.LENGTH_SHORT).show();
                            }else {
                                if (myClient.isConnect()){
                                    try {
                                        if (myClient.isPassive()){
                                            myClient.setPassiveFast();
                                        }else {
                                            myClient.setActiveFast();
                                        }
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    } catch (ModeFailure modeFailure) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,modeFailure.getMessage(),Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                        dialog.dismiss();
                                    }
                                }else {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(MainActivity.this,"????????????",Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                    dialog.dismiss();
                                }
                            }
                        }
                    }
                });

                builder6.setPositiveButton("??????", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int i) {
                        dialog.dismiss();
                    }
                });

                AlertDialog dialog6 = builder6.create();
                dialog6.show();
                break;
            case android.R.id.home:
                mDrawerLayout.openDrawer(GravityCompat.START);
                break;
        }
        return true;
    }

    //??????????????????
    private void InitMenu() {
        NavigationView navigationView=(NavigationView) findViewById(R.id.nav_view);
        MenuItem checkedItem = navigationView.getCheckedItem();
        if (checkedItem!=null && checkedItem.getItemId() == R.id.nav_close){
            checkedItem.setChecked(false);
        }
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @SuppressLint({"NonConstantResourceId", "NotifyDataSetChanged"})
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()){
                    //???????????????
                    case R.id.nav_connecting:
                        Intent intent=new Intent(MainActivity.this,ConnectActivity.class);
                        startActivity(intent);
                        break;
                    //?????????????????????
                    case R.id.nav_FtpServerDirectory:
                        if (myClient!=null && myClient.isConnect()){
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        List<FTPFile> files = myClient.list("/");
                                        for (FTPFile file : files) {
                                            file.setPath(file.getFilename());
                                            boolean check = false;
                                            for (FTPFile ftpFile : ftpList) {
                                                if (ftpFile.getFilename().equals(file.getFilename())) {
                                                    check = true;
                                                    break;
                                                }
                                            }
                                            if (!check)
                                                ftpList.add(file);
                                        }
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                FTPAdapter=new ServerFileAdapter(ftpList,MainActivity.this);
                                                FTPAdapter.setMyClient(myClient);
                                                recyclerView.setAdapter(FTPAdapter);
                                                FTPAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    } catch (SocketError | DownloadException e) {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(MainActivity.this,e.getMessage(),Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                }
                            }).start();
                        }else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(!FtpUtil.getMyClient().isConnect())
                                        Toast.makeText(MainActivity.this,"?????????????????????????????????????????????????????????",Toast.LENGTH_SHORT).show();
                                }
                            });
                            item.setChecked(false);
                        }
                        mDrawerLayout.closeDrawer(GravityCompat.START);
                        break;
                    //??????????????????
                    case R.id.nav_LocalDirectory:
                        if (recyclerView!=null){
                            InitData();
                            adapter.setArraylist(fileList);
                            recyclerView.setAdapter(adapter);
                            adapter.notifyDataSetChanged();
                        }
                        mDrawerLayout.closeDrawer(GravityCompat.START);
                        break;
                    //????????????
                    case R.id.nav_close:
                        item.setChecked(false);
                        disconnect();
                        mDrawerLayout.closeDrawer(GravityCompat.START);
                        break;
                }
                return true;
            }
        });
    }

    //????????????
    @SuppressLint("NotifyDataSetChanged")
    @Override
    public void onBackPressed(){
        if(recyclerView!=null && recyclerView.getAdapter()==adapter) {
            adapter.LastFile();
        } else {
            FTPAdapter.notifyDataSetChanged();
            FTPAdapter.LastFile();
        }
    }

    //????????????
    private void getPermissions() {
        //SD?????????
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED  || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)!= PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},10086);
        }else {
            setContentView(R.layout.activity_main);
            Init();
        }
    }

    //??????????????????
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10086) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setContentView(R.layout.activity_main);
            } else {
                Toast.makeText(this, "??????????????????????????????", Toast.LENGTH_SHORT).show();
                setContentView(R.layout.activity_cannot_get_file);
            }
            Init();
        }
    }

    //????????????
    private boolean InitData() {
        File file = Environment.getExternalStorageDirectory();
        if (file.listFiles() != null){
            fileList= new ArrayList<>(Arrays.asList(Objects.requireNonNull(file.listFiles())));
            return true;
        }else {
            fileList=new ArrayList<>();
            Toast.makeText(this, "??????????????????", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    //broadcast
    class ConnectBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.connected".equals(intent.getAction())) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        myClient= FtpUtil.getMyClient();
                    }
                }).start();
            }
        }
    }

    private void InitBroadCast() {
        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction("com.example.connected");
        ConnectBroadcastReceiver connectBroadcastReceiver = new ConnectBroadcastReceiver();
        registerReceiver(connectBroadcastReceiver,intentFilter);
    }

    //????????????
    private void disconnect(){
        if (FtpUtil.getMyClient()!=null && FtpUtil.getMyClient().isConnect()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (FtpUtil.getMyClient().isConnect()) {//??????????????????
                            FtpUtil.getMyClient().disconnect();
                            ftpList.clear();
                            runOnUiThread(new Runnable() {
                                @SuppressLint("NotifyDataSetChanged")
                                @Override
                                public void run() {
                                    if (FTPAdapter!=null){
                                        FTPAdapter.notifyDataSetChanged();
                                    }
                                }
                            });
                            threadToast("???????????????");
                        } else {
                            //??????????????????
                            threadToast("??????????????????????????????");
                        }
                    } catch (IOException e) {
                        threadToast("?????????????????????");
                    }
                }
            }).start();
        }else {
            threadToast("??????????????????????????????");
        }
    }

    public void threadToast(String text){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    class mHandler extends Handler {
        // ????????????handlerMessage() ??????????????????UI?????????
        @Override
        public void handleMessage(Message msg) {
            // ??????UI??????
            if (msg.what == 1) {
                if (toast == null) {
                    toast = Toast.makeText(MyApplication.getContext(), msg.obj.toString(), Toast.LENGTH_SHORT);
                } else {
                    toast.cancel();
                    toast = Toast.makeText(MyApplication.getContext(), msg.obj.toString(), Toast.LENGTH_SHORT);
                }
                toast.show();
            }
        }
    }

    mHandler mHandler = new mHandler();

    public Handler getmHandler() {
        return mHandler;
    }
}