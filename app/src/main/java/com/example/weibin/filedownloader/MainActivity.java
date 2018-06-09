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

    private final String url1 = "http://gdown.baidu.com/data/wisegame/93524fb7a7dc0528/QQ_864.apk";
    private final String url2 = "http://gdown.baidu.com/data/wisegame/785f37df5d72c409/weixin_1320.apk";
    private ProgressBar progressBar;
    private ProgressBar progressBar1;
    FileDownLoader loader = null;
    FileDownLoader mLoader = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button downloadButton = (Button) findViewById(R.id.startDownload);
        Button pause = (Button) findViewById(R.id.pauseDownload);
        Button cancel = (Button) findViewById(R.id.cancelDownload);
        progressBar = (ProgressBar) findViewById(R.id.progress);
        downloadButton.setOnClickListener(this);
        pause.setOnClickListener(this);
        cancel.setOnClickListener(this);
        loader = new FileDownLoader(this);

        Button downloadButton1 = (Button) findViewById(R.id.startDownload1);
        Button pause1 = (Button) findViewById(R.id.pauseDownload1);
        Button cancel1 = (Button) findViewById(R.id.cancelDownload1);
        progressBar1 = (ProgressBar) findViewById(R.id.progress1);
        downloadButton1.setOnClickListener(this);
        pause1.setOnClickListener(this);
        cancel1.setOnClickListener(this);
        mLoader = new FileDownLoader(this);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case 1:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    loader.setView(progressBar).with(url1).use(3).start();
                    mLoader.setView(progressBar1).with(url2).use(5).start();
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
                    loader.setView(progressBar).with(url1).use(3).start();
                }
                break;
            case R.id.pauseDownload:
                loader.pause();
                break;
            case R.id.cancelDownload:
                loader.cancel();
                break;
            case R.id.startDownload1:
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]
                            {Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                } else {
                    mLoader.setView(progressBar1).with(url2).use(5).start();
                }
                break;
            case R.id.pauseDownload1:
                mLoader.pause();
                break;
            case R.id.cancelDownload1:
                mLoader.cancel();
                break;
                default:
                    break;
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
