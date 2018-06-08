package com.example.weibin.filedownloader.FileLoader;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownLoadThread implements Runnable, UpdateStateListener.DownLoaStateListener{

    public boolean IS_DOWNLOAD = true;
    private File mFile;
    private String downloadUrl;
    private Context mContext;
    private int threadId;
    private long startPosition;
    private long endPosition;
    private long threadProgress = 0;
    private UpdateStateListener listener;

    /**
     * @param mFile 保存的文件
     * @param downloadUrl 下载的url
     * @param threadId 当前线程的id,区别线程
     * @param startPosition 断点续传请求及保存文件的开始位置
       @param endPosition 断点续传请求及保存文件的结束的位置
       @param listener 更新下载进度的回调
     * */
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
            String[] strings = mFile.getName().split(File.separator);
            SharedPreferences.Editor editor = mContext.getSharedPreferences(strings[strings.length - 1], Context.MODE_PRIVATE).edit();
            in = urlConnection.getInputStream();
            byte[] bytes = new byte[10240];
            int len = 0;
            while ((len = in.read(bytes)) != -1){
                if (!IS_DOWNLOAD) {
                    break;
                }
                ranFile.write(bytes, 0, len);
                threadProgress += len;
                editor.putLong(mFile.getName() + threadId, threadProgress + startPosition).apply();
                listener.updateTaskSize(len);
            }
            in.close();
            ranFile.close();
            urlConnection.disconnect();
            listener.is_success();
        } catch (Exception e) {
            e.printStackTrace();
            listener.onfailure();
        }
    }

    @Override
    public void isDownLoading(boolean isDownLoading) {
        this.IS_DOWNLOAD = isDownLoading;
    }
}
