package com.example.weibin.filedownloader.FileLoader;

public interface UpdateStateListener {
    void updateTaskSize(long size);
    void is_success();
    void onfailure();

    interface DownLoaStateListener{
        void isDownLoading(boolean isDownLoading);
    }
}
