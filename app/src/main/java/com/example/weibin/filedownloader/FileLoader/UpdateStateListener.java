package com.example.weibin.filedownloader.FileLoader;

public interface UpdateStateListener {
    void updateTaskSize(long size);
    void successDownload();
}
