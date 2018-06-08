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
