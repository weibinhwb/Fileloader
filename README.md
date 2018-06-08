# Fileloader
#### 使用方法
~~~ 
//构造器传入Context
FileDownLoader loader = new FileDownLoader(this); 

// setView()传入ProgressBar作为显示器，with()传入下载路径，use()使用的线程数，start()开始下载
loader.setView(progressBar).with(url).use(3).start();

//暂停
loader.pause();

//删除
loader.cancle();
~~~
#### 注释 apk示例下载的是手机QQ
#### gif演示
![基本操作](https://github.com/weibinhwb/Fileloader/blob/master/app/src/gif/video2gif_20180608_133105.gif)
![基本操作](https://github.com/weibinhwb/Fileloader/blob/master/app/src/gif/video2gif_20180608_133210.gif)
