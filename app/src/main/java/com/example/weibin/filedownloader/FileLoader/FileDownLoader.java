package com.example.weibin.filedownloader.FileLoader;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static com.example.weibin.filedownloader.FileLoader.UpdateStateListener.*;

public class FileDownLoader{

    private File file;
    private String downloadUrl;
    private Context context;
    private int threadNum;
    private long taskProgress;
    private long fileLength;
    private long freeMemory;
    private ProgressBar progressBar;
    private String TEMP_NAME;
    private List<DownLoaStateListener> mListeners;
    private static final String TASK_PROGRESS = "task_progress";
    private ExecutorService sCachedThreadPool;
    private boolean startAble = true;
    private boolean pauseAble = false;
    private boolean cancelAble = false;

    public FileDownLoader(Context context){
        this.context = context;
    }

    public FileDownLoader with(String url) {
        this.downloadUrl = url;
        String directory =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        String filename = url.substring(url.lastIndexOf(File.separator));
        file = new File(directory + filename);
        String[] strings = filename.split(File.separator);
        TEMP_NAME = strings[strings.length - 1];
        freeMemory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getFreeSpace();
        return this;
    }

    public FileDownLoader use(int mThreadNum) {
        this.threadNum = mThreadNum;
        mListeners = new ArrayList<>();
        sCachedThreadPool = Executors.newFixedThreadPool(mThreadNum);
        return this;
    }

    public void pause(){
        if (!pauseAble)
            return;
        pauseAble = false;
        for (int i = 0; i < threadNum; i ++){
            mListeners.get(i).isDownLoading(false);
        }
        showToast("暂停下载");
        startAble = true;
        cancelAble = true;
    }

    public void cancel(){
        if (!cancelAble)
            return;
        cancelAble = false;
        if (progressBar != null)
            progressBar.setProgress(0);
        for (int i = 0; i < threadNum; i ++){
            mListeners.get(i).isDownLoading(false);
        }
        if (file.exists() && file.delete()){
            deleteStateSave();
            showToast("删除成功");
        }
        pauseAble = false;
        startAble = true;
    }

    public FileDownLoader setView(ProgressBar progressBar){
        this.progressBar = progressBar;
        return this;
    }

    public void start(){
        if (!startAble)
            return;
        startAble = false;
        taskProgress = getTaskProgress();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(downloadUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    final int code = connection.getResponseCode();
                    if (code != 200){
                        showToast("连接失败");
                        return;
                    }
                    fileLength = connection.getContentLength();
                    connection.disconnect();
                    //判断手机内存是否足够
                    if (fileLength > freeMemory){
                        showToast("内存不足");
                        return;
                    }
                    long downBlock = fileLength / threadNum;
                    SharedPreferences preferences = context.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE);
                    for (int i = 0; i < threadNum; i ++){
                        long start = preferences.getLong(TEMP_NAME + i, i * downBlock);
                        long end = downBlock *  (i + 1) - 1;
                        if (i == threadNum - 1){
                            end = fileLength - 1;
                        }
                        DownLoadThread run = new DownLoadThread(file, downloadUrl, i, start, end, context, new UpdateStateListener() {
                            @Override
                            public void updateTaskSize(long size) {
                                synchronized (FileDownLoader.this){
                                    taskProgress += size;
                                    updateTaskProgress();
                                    updateProgressBar();
                                }
                            }
                            @Override
                            public void is_success() {
                                if (fileLength == taskProgress){
                                    sCachedThreadPool.shutdown();
                                    deleteStateSave();
                                    showToast("下载成功");
                                }
                            }
                            @Override
                            public void onfailure() {
                                showToast("下载失败");
                            }
                        });
                        sCachedThreadPool.execute(run);
                        mListeners.add(run);
                    }
                    cancelAble = true;
                    pauseAble = true;
                } catch (NullPointerException | IOException e){
                    e.printStackTrace();
                    showToast("下载失败");
                }
            }
        }).start();
    }

    private long getTaskProgress(){
        SharedPreferences preferences = context.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE);
        return preferences.getLong(TASK_PROGRESS, 0);
    }

    private void updateTaskProgress(){
        SharedPreferences.Editor editor = context.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE).edit();
        editor.putLong(TASK_PROGRESS, taskProgress).apply();
    }

    private void updateProgressBar(){
        if (progressBar != null){
            progressBar.setMax((int) fileLength);
            progressBar.setProgress((int)taskProgress);
        }
    }

    private void deleteStateSave(){
        SharedPreferences.Editor editor = context.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE).edit();
        editor.clear().apply();
    }
    private Activity getActivity(){
        return (AppCompatActivity) context;
    }

    private void showToast(final String text){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
