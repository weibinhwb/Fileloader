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
import java.io.InputStream;
import java.io.RandomAccessFile;
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
    private static final String TASK_PROGRESS = "task_progress";
    private ExecutorService sCachedThreadPool;
    private volatile boolean startAble = true;
    private volatile boolean pauseAble = false;
    private volatile boolean cancelAble = false;

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
        sCachedThreadPool = Executors.newFixedThreadPool(mThreadNum);
        return this;
    }

    public void pause(){
        if (!pauseAble)
            return;
        setPauseAble(false);
        showToast("暂停下载");
        setStartAble(true);
        setCancelAble(true);
    }

    public void cancel(){
        if (!cancelAble)
            return;
        setCancelAble(false);
        if (progressBar != null)
            progressBar.setProgress(0);
        if (file.exists() && file.delete()){
            deleteStateSave();
            showToast("删除成功");
        }
        setPauseAble(false);
        setStartAble(true);
    }

    public FileDownLoader setView(ProgressBar progressBar){
        this.progressBar = progressBar;
        return this;
    }

    public void start(){
        if (!isStartAble())
            return;
        setStartAble(false);
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
                    setFileLength(connection.getContentLength());
                    connection.disconnect();
                    //判断手机内存是否足够
                    if (getFileLength() > freeMemory){
                        showToast("内存不足");
                        return;
                    }
                    long downBlock = getFileLength() / threadNum;
                    SharedPreferences preferences = context.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE);
                    for (int i = 0; i < threadNum; i ++){
                        long start = preferences.getLong(TEMP_NAME + i, i * downBlock);
                        long end = downBlock *  (i + 1) - 1;
                        if (i == threadNum - 1){
                            end = getFileLength() - 1;
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
                                if (getFileLength() == getTaskProgress()){
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
                    }
                    setCancelAble(true);
                    setPauseAble(true);
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

    private synchronized long getFileLength() {
        return fileLength;
    }

    private synchronized void setFileLength(long fileLength) {
        this.fileLength = fileLength;
    }

    private synchronized boolean isStartAble() {
        return startAble;
    }

    private synchronized void setStartAble(boolean startAble) {
        this.startAble = startAble;
    }

    private synchronized void setPauseAble(boolean pauseAble) {
        this.pauseAble = pauseAble;
    }

    private synchronized void setCancelAble(boolean cancelAble) {
        this.cancelAble = cancelAble;
    }

    class DownLoadThread implements Runnable{

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
                    if (!cancelAble || !pauseAble ){
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

    }
}
