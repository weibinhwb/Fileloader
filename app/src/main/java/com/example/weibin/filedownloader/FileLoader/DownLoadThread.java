package com.example.weibin.filedownloader.FileLoader;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownLoadThread implements Runnable{

    public static  boolean IS_DOWNLOAD = true;
    public static final String SAVE_THREAD_STATE = "save_thread_state";
    private File mFile;
    private String downloadUrl;
    private Context mContext;
    private int threadId;
    private long startPosition;
    private long endPosition;
    private long threadProgress = 0;
    private UpdateStateListener listener;

    public DownLoadThread(File mFile, String downloadUrl, int threadId, long startPosition,
                          long endPosition, Context context, UpdateStateListener listener) {
        this.mFile = mFile;
        this.downloadUrl = downloadUrl;
        this.threadId = threadId;
        this.startPosition = startPosition;
        this.endPosition = endPosition;
        this.mContext = context;
        this.listener = listener;
    }

    @Override
    public void run() {
        HttpURLConnection urlConnection = null;
        InputStream in = null;
        RandomAccessFile ranFile = null;
        try {
            ranFile = new RandomAccessFile(mFile, "rw");
            ranFile.seek(startPosition);
            URL url = new URL(downloadUrl);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Range", "bytes=" + startPosition + "-" + endPosition);
            int code = urlConnection.getResponseCode();
            if (code != 206)
                return;
            SharedPreferences.Editor editor = mContext.getSharedPreferences(SAVE_THREAD_STATE, Context.MODE_PRIVATE).edit();
            in = urlConnection.getInputStream();
            byte[] bytes = new byte[10240];
            int len = 0;
            while ((len = in.read(bytes)) != -1){
                if (!IS_DOWNLOAD) {
                    break;
                }
                ranFile.write(bytes, 0, len);
                threadProgress += len;
                Log.d("" + this, threadProgress + "");
                editor.putLong(SAVE_THREAD_STATE + threadId, threadProgress + startPosition).apply();
                listener.updateTaskSize(len);
            }
            in.close();
            ranFile.close();
            urlConnection.disconnect();
            listener.successDownload();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
