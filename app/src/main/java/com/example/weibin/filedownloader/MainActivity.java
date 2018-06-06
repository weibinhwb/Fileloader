package com.example.weibin.filedownloader;

import android.Manifest;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.weibin.filedownloader.FileLoader.FileDownLoader;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private final String url = "http://down.sandai.net/ThunderVIP/ThunderVIP-ugw.exe";
    private ProgressBar progressBar;
    FileDownLoader loader = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button downloadButton = (Button) findViewById(R.id.startDownload);
        Button pause = (Button) findViewById(R.id.pauseDownload);
        Button refresh = (Button) findViewById(R.id.refreshDownload);
        Button cancel = (Button) findViewById(R.id.cancelDownload);
        downloadButton.setOnClickListener(this);
        pause.setOnClickListener(this);
        refresh.setOnClickListener(this);
        cancel.setOnClickListener(this);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        loader = FileDownLoader.init(this);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    loader.setView(progressBar).with(url).use(3).start();
                } else {
                    Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.startDownload:
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]
                            {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    loader.setView(progressBar).with(url).use(3).start();
                }
                break;
            case R.id.pauseDownload:
                loader.pause();
                break;
            case R.id.refreshDownload:
                loader.refresh();
                break;
            case R.id.cancelDownload:
                loader.cancle();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        loader.pause();
    }
}
