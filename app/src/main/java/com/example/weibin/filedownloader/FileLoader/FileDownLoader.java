package com.example.weibin.filedownloader.FileLoader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final String TASK_PROGRESS = "task_progress";
    private ExecutorService fixedThreadPool = null;
    private FileDownLoader(Context context){
        this.context = context;
    }

    public static FileDownLoader init(Context context){
        if (fileDownLoader == null){
            synchronized (FileDownLoader.class){
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
        fixedThreadPool = Executors.newFixedThreadPool(mThreadNum);
        this.threadNum = mThreadNum;
        return this;
    }

    public void pause(){
        IS_DOWNLOAD = false;
        Log.d(TAG , "暂停成功");
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, "暂停下载", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void cancel(){
        this.pause();
        if (progressBar != null)
            progressBar.setProgress(0);
        if (file.exists() && file.delete()){
            SharedPreferences.Editor editor = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE).edit();
            editor.clear().apply();
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(context, "删除成功", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
//
//    public void refresh(){
//        if (progressBar != null)
//            progressBar.setProgress((int)taskProgress);
//        this.start();
//        getActivity().runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                Toast.makeText(context, "恢复下载", Toast.LENGTH_SHORT).show();
//            }
//        });
//    }

    public FileDownLoader setView(ProgressBar progressBar){
        this.progressBar = progressBar;
        return this;
    }

    public FileDownLoader start(){
        IS_DOWNLOAD = true;
        SharedPreferences preferences = context.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE);
        taskProgress = preferences.getLong(TASK_PROGRESS, 0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                RandomAccessFile accessFile = null;
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
                    accessFile = new RandomAccessFile(file, "rw");
                    accessFile.setLength(fileLength);
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
                                    editor.putLong(TASK_PROGRESS, taskProgress).apply();
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
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            Toast.makeText(context, "下载成功", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            }
                        });
                        fixedThreadPool.execute(run);
                    }
                } catch (NullPointerException | IOException e){
                    e.printStackTrace();
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "下载失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
        return this;
    }

    private Activity getActivity(){
        return (AppCompatActivity) context;
    }
}
