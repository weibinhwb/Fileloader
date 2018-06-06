package com.example.weibin.filedownloader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

import static android.content.Context.MODE_PRIVATE;


public class Demo {

    private String mDownUrl;
    private int mThreadNum;
    private File mFile;
    private Context mContext;
    private long length;
    private long currentProgress = 0;
    private static Demo fileLoader;
    private ProgressBar progressBar;
    private final static String SAVE_STATE = "TASK";
    private final static String IS_COMPLETE = "IS_COMPLETE";
    private final static String CURRENT_STATE = "CURRENT_STATE";

    private Demo(Context context) {
        this.mContext = context;
    }

    public static Demo init(Context context){
        if (fileLoader == null){
            synchronized (Demo.class){
                if (fileLoader == null)
                    fileLoader = new Demo(context);
            }
        }
        return fileLoader;
    }

    public Demo with(String mDownUrl) {
        this.mDownUrl = mDownUrl;
        String directory =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        String filename = mDownUrl.substring(mDownUrl.lastIndexOf(File.separator));
        mFile = new File(directory + filename);
        return this;
    }

    public Demo use(int mThreadNum) {
        this.mThreadNum = mThreadNum;
        return this;
    }

    public Demo setView(ProgressBar progressBar){
        this.progressBar = progressBar;
        return this;
    }

    private synchronized void setCurrentProgress(long progress) {
        currentProgress += progress;
    }

    private synchronized long getCurrentProgress() {
        return currentProgress;
    }

    public void start(){
        SharedPreferences query = mContext.getSharedPreferences(SAVE_STATE, Context.MODE_PRIVATE);
        boolean isFinish = query.getBoolean(IS_COMPLETE, false);
        if (mFile.exists() && isFinish){
            return;
        } else if (!mFile.exists() && isFinish){
            SharedPreferences.Editor editor = query.edit();
            editor.remove(IS_COMPLETE).apply();
        }
        currentProgress = query.getLong(CURRENT_STATE, 0);
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(mDownUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    int code = connection.getResponseCode();
                    if (code != 200)
                        return;
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(1000 * 5);
                    connection.setReadTimeout(1000 * 5);
                    length = connection.getContentLength();
                    progressBar.setMax((int)length);
                    Log.d("length", length + "");
                    connection.disconnect();
                    //循环分配任务
                    long downSize = length / 3 + 1;
                    long currentSize;
                    boolean isComplete;
                    SharedPreferences preferences = mContext.getSharedPreferences(SAVE_STATE, MODE_PRIVATE);
                    for (int i = 1; i <= mThreadNum; i++) {
                        //判断线程是否完成
                        isComplete = preferences.getBoolean(IS_COMPLETE + i, false);
                        if (isComplete)
                            continue;
                        //分配下载区间
                        currentSize = preferences.getLong( CURRENT_STATE + i, 0);
                        long startPosition = currentSize + downSize * (i - 1);
                        long lastPosition = downSize * i;
                        if (i == mThreadNum)
                            lastPosition = length;
                        DownLoadThread downLoadThread = new DownLoadThread( startPosition, lastPosition, i);
                        Thread thread = new Thread(downLoadThread);
                        thread.start();
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();
    }


    private class DownLoadThread implements Runnable{

        private long lastPosition;
        private long startPosition;
        private int threadId;
        private DownLoadThread(long startPosition, long lastPosition, int i) {
            this.lastPosition = lastPosition;
            this.startPosition = startPosition;
            this.threadId = i;
        }

        @Override
        public void run() {
            HttpURLConnection urlConnection = null;
            RandomAccessFile fragFile = null;
            try {
                fragFile = new RandomAccessFile(mFile, "rw");
                fragFile.seek(startPosition);
                URL url = new URL(mDownUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setConnectTimeout(1000 * 5);
                urlConnection.setReadTimeout(1000 * 5);
                urlConnection.setRequestProperty("Range", "bytes=" + startPosition + "-" + (lastPosition));
                urlConnection.setRequestMethod("GET");
                int code = urlConnection.getResponseCode();
                if (code != 206)
                    return;
                InputStream in = urlConnection.getInputStream();
                byte[] bytes = new byte[10240];
                int len = 0;
                long currentSize = 0;
                SharedPreferences.Editor editor = mContext.getSharedPreferences(SAVE_STATE, MODE_PRIVATE).edit();
                while ((len = in.read(bytes)) != -1){
                    fragFile.write(bytes, 0, len);
                    setCurrentProgress(len);
                    progressBar.setProgress((int) getCurrentProgress());
                    currentSize += len;
                    editor.putLong(CURRENT_STATE + threadId, currentSize).apply();
                    editor.putLong(CURRENT_STATE, getCurrentProgress()).apply();
                }
                editor.putBoolean(IS_COMPLETE + threadId, true).apply();
                in.close();
                urlConnection.disconnect();
                fragFile.close();
                Log.d("finally currentProgress", getCurrentProgress() + "and file length" + length);
                if (getCurrentProgress() == length + mThreadNum - 1){
                    editor.clear().apply();
                    editor = mContext.getSharedPreferences(SAVE_STATE, MODE_PRIVATE).edit();
                    editor.putBoolean(IS_COMPLETE, true).apply();
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}
