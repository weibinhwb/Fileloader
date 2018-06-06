package com.example.weibin.filedownloader.FileLoader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.weibin.filedownloader.Demo;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.example.weibin.filedownloader.FileLoader.DownLoadThread.IS_DOWNLOAD;
import static com.example.weibin.filedownloader.FileLoader.DownLoadThread.SAVE_THREAD_STATE;

public class FileDownLoader{

    private File file;
    private String downloadUrl;
    private Context context;
    private int threadNum;
    private long taskProgress;
    private long fileLength;
    private ProgressBar progressBar;
    private static FileDownLoader fileDownLoader;
    private static final String TAG = "FileDownLoader";
    private static final String TASKPROGRESS = "task_progress";

    private FileDownLoader(Context context){
        this.context = context;
    }

    public static FileDownLoader init(Context context){
        if (fileDownLoader == null){
            synchronized (Demo.class){
                if (fileDownLoader == null)
                    fileDownLoader = new FileDownLoader(context);
            }
        }
        return fileDownLoader;
    }

    public FileDownLoader with(String url) {
        this.downloadUrl = url;
        String directory =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        String filename = url.substring(url.lastIndexOf(File.separator));
        file = new File(directory + filename);
        return this;
    }

    public FileDownLoader use(int mThreadNum) {
        this.threadNum = mThreadNum;
        return this;
    }

    public void pause(){
        IS_DOWNLOAD = false;
        Log.d(TAG , "暂停成功");
    }

    public void cancle(){
        this.pause();
        if (progressBar != null)
            progressBar.setProgress(0);
        if (file.exists() && file.delete()){
            SharedPreferences.Editor editor = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE).edit();
            editor.clear().apply();
            Log.d(TAG , "删除成功");
        }
    }

    public void refresh(){
        if (progressBar != null)
            progressBar.setProgress((int)taskProgress);
        this.start();
    }

    public FileDownLoader setView(ProgressBar progressBar){
        this.progressBar = progressBar;
        return this;
    }

    public FileDownLoader start(){
        IS_DOWNLOAD = true;
        SharedPreferences preferences = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE);
        taskProgress = preferences.getLong(TASKPROGRESS, 0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
//                RandomAccessFile accessFile = null;
                try {
                    URL url = new URL(downloadUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    final int code = connection.getResponseCode();
                    if (code != 200){
                        Log.d(TAG , "连接失败");
                        return;
                    }
                    fileLength = connection.getContentLength();
                    long downBlock = fileLength / threadNum;
//                    accessFile.setLength(length);
                    connection.disconnect();
                    final SharedPreferences preferences = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE);
                    for (int i = 0; i < threadNum; i ++){
                        long start = preferences.getLong(SAVE_THREAD_STATE + i, i * downBlock);
                        long end = downBlock *  (i + 1) - 1;
                        if (i == threadNum - 1){
                            end = fileLength - 1;
                        }
                        DownLoadThread run = new DownLoadThread(file, downloadUrl, i, start, end, context, new UpdateStateListener() {
                            @Override
                            public void updateTaskSize(long size) {
                                synchronized (FileDownLoader.this){
                                    SharedPreferences.Editor editor = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE).edit();
                                    taskProgress += size;
                                    editor.putLong(TASKPROGRESS, taskProgress).apply();
                                    progressBar.setMax((int) fileLength);
                                    progressBar.setProgress((int)taskProgress);
                                    Log.d(this + "updateTask", taskProgress + "||" + fileLength);
                                }
                            }
                            @Override
                            public void successDownload() {
                                if (fileLength == taskProgress){
                                    SharedPreferences.Editor editor = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE).edit();
                                    editor.clear().apply();
                                    Log.d(TAG , "下载成功");
                                }
                            }
                        });
                        Thread thread = new Thread(run);
                        thread.start();
                    }
                } catch (NullPointerException | IOException e){
                    e.printStackTrace();
                }
            }
        }).start();
        return this;
    }
}
