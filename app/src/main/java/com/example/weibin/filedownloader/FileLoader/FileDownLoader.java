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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileDownLoader{

    private File mFile;
    private String mDownloadUrl;
    private Context mContext;
    private int mThreadNum;
    private long mTaskProgress;
    private long mFileLength;
    private long mFreeMemory;
    private ProgressBar mProgressBar;
    private String TEMP_NAME;
    private static final String TASK_PROGRESS = "task_progress";
    private ExecutorService sCachedThreadPool;
    private volatile boolean mStartAble = true;
    private volatile boolean mPauseAble = false;
    private volatile boolean mCancelAble = false;

    public FileDownLoader(Context context){
        this.mContext = context;
    }

    public FileDownLoader with(String url) {
        this.mDownloadUrl = url;
        createFile(url);
        mFreeMemory = getFreeSpace();
        return this;
    }

    public FileDownLoader use(int mThreadNum) {
        this.mThreadNum = mThreadNum;
        sCachedThreadPool = Executors.newFixedThreadPool(mThreadNum);
        return this;
    }

    public void pause(){
        if (!mPauseAble)
            return;
        setPauseAble(false);
        showToast("暂停下载");
        setStartAble(true);
        setCancelAble(true);
    }

    public void cancel(){
        if (!mCancelAble)
            return;
        setCancelAble(false);
        updateProgressBar(0, 0);
        if (mFile.exists() && mFile.delete()){
            deleteStateSave();
            showToast("删除成功");
        }
        setPauseAble(false);
        setStartAble(true);
    }

    public FileDownLoader setView(ProgressBar progressBar){
        this.mProgressBar = progressBar;
        return this;
    }

    public void start(){
        if (!isStartAble())
            return;
        setStartAble(false);
        mTaskProgress = getTaskProgress();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(mDownloadUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    final int code = connection.getResponseCode();
                    if (code != 200){
                        showToast("连接失败");
                        return;
                    }
                    setFileLength(connection.getContentLength());
                    connection.disconnect();
                    //判断手机内存是否足够
                    if (getFileLength() > mFreeMemory){
                        showToast("内存不足");
                        return;
                    }
                    long downBlock = getFileLength() / mThreadNum;
                    SharedPreferences preferences = mContext.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE);
                    for (int i = 0; i < mThreadNum; i ++){
                        long start = preferences.getLong(TEMP_NAME + i, i * downBlock);
                        long end = downBlock *  (i + 1) - 1;
                        if (i == mThreadNum - 1){
                            end = getFileLength() - 1;
                        }
                        DownLoadThread run = new DownLoadThread(mFile, i, start, end, new UpdateStateListener() {
                            @Override
                            public void updateTaskSize(long size) {
                                synchronized (FileDownLoader.this){
                                    mTaskProgress += size;
                                    updateTaskProgress();
                                    updateProgressBar((int)mFileLength, (int)mTaskProgress);
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

    class DownLoadThread implements Runnable{

        private File file;
        private int threadId;
        private long startPosition;
        private long endPosition;
        private long threadProgress = 0;
        private UpdateStateListener listener;

        /**
         * @param mFile 保存的文件
         * @param threadId 当前线程的id,区别线程
         * @param startPosition 断点续传请求及保存文件的开始位置
           @param endPosition 断点续传请求及保存文件的结束的位置
           @param listener 更新下载进度的回调
         * */
        public DownLoadThread(File mFile, int threadId, long startPosition,
                              long endPosition, UpdateStateListener listener) {
            this.file = mFile;
            this.threadId = threadId;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.listener = listener;
        }

        @Override
        public void run() {
            HttpURLConnection urlConnection = null;
            InputStream in = null;
            RandomAccessFile ranFile = null;
            try {
                ranFile = new RandomAccessFile(file, "rw");
                ranFile.seek(startPosition);
                URL url = new URL(mDownloadUrl);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Range", "bytes=" + startPosition + "-" + endPosition);
                int code = urlConnection.getResponseCode();
                if (code != 206)
                    return;
                String[] strings = file.getName().split(File.separator);
                SharedPreferences.Editor editor = mContext.getSharedPreferences(strings[strings.length - 1], Context.MODE_PRIVATE).edit();
                in = urlConnection.getInputStream();
                byte[] bytes = new byte[10240];
                int len = 0;
                while ((len = in.read(bytes)) != -1){
                    if (!mCancelAble || !mPauseAble){
                        break;
                    }
                    ranFile.write(bytes, 0, len);
                    threadProgress += len;
                    editor.putLong(file.getName() + threadId, threadProgress + startPosition).apply();
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


    private void createFile(String url){
        String directory =  Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        String filename = url.substring(url.lastIndexOf(File.separator));
        mFile = new File(directory + filename);
        String[] strings = filename.split(File.separator);
        TEMP_NAME = strings[strings.length - 1];
    }

    private long getFreeSpace(){
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getFreeSpace();
    }

    private long getTaskProgress(){
        SharedPreferences preferences = mContext.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE);
        return preferences.getLong(TASK_PROGRESS, 0);
    }

    private void updateTaskProgress(){
        SharedPreferences.Editor editor = mContext.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE).edit();
        editor.putLong(TASK_PROGRESS, mTaskProgress).apply();
    }

    private void updateProgressBar(int max, int progress){
        if (mProgressBar != null){
            mProgressBar.setMax(max);
            mProgressBar.setProgress(progress);
        }
    }

    private void deleteStateSave(){
        SharedPreferences.Editor editor = mContext.getSharedPreferences(TEMP_NAME, Context.MODE_PRIVATE).edit();
        editor.clear().apply();
    }
    private Activity getActivity(){
        return (AppCompatActivity) mContext;
    }

    private void showToast(final String text){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(mContext, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private synchronized long getFileLength() {
        return mFileLength;
    }

    private synchronized void setFileLength(long fileLength) {
        this.mFileLength = fileLength;
    }

    private synchronized boolean isStartAble() {
        return mStartAble;
    }

    private synchronized void setStartAble(boolean startAble) {
        this.mStartAble = startAble;
    }

    private synchronized void setPauseAble(boolean pauseAble) {
        this.mPauseAble = pauseAble;
    }

    private synchronized void setCancelAble(boolean cancelAble) {
        this.mCancelAble = cancelAble;
    }
}
