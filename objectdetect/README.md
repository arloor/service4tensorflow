# Object Dection Service

参考github tensorflow/model项目下的java object_detect代码，进行修改生成的restful api风格的tensorflow服务

[参考的原代码及说明地址](https://github.com/tensorflow/models/tree/de2842408a1790a56718c293e01e0d555fa84035/samples/languages/java/object_detection)链接所指代码的版本就是使用的版本

## 唯一依赖 tensorflow 1.8.0版本

1. 安装python3.6.x
2. 指定1.8版本安装tensorflow pip3 install --upgrade --ignore-installed  --upgrade tensorflow==1.8.0

## 运行例子

```
请求：
POST http://localhost/service
Accept: */*
Cache-Control: no-cache
content-type: application/json

[
    "http://jianbujingimages.moontell.cn/FrrkTtsITfXki44oJqk6i3IUzv2x",
    "http://jianbujingimages.moontell.cn/FhD-asgS-HOuUssL1dVzmgkhD2v-"
]

响应：
HTTP/1.1 200 
Content-Type: application/json;charset=UTF-8
Transfer-Encoding: chunked
Date: Tue, 17 Jul 2018 08:33:58 GMT
Connection: close
Proxy-Connection: keep-alive

[
  {
    "imageURL": "http://jianbujingimages.moontell.cn/FrrkTtsITfXki44oJqk6i3IUzv2x",
    "detectCells": [
      "Found person (score: 0.9353)",
      "Found laptop (score: 0.8388)",
      "Found keyboard (score: 0.6445)"
    ]
  },
  {
    "imageURL": "http://jianbujingimages.moontell.cn/FhD-asgS-HOuUssL1dVzmgkhD2v-",
    "detectCells": [
      "Found cup (score: 0.9900)",
      "Found cell phone (score: 0.9838)",
      "Found mouse (score: 0.9833)"
    ]
  }
]

Response code: 200; Time: 7834ms; Content length: 381 bytes
```

请求体是网络图片的url数组。响应中给出了每个图片对应的检测结果。

`"Found mouse (score: 0.9833)"`表示 探测到了鼠标

## 其他

把tensorflow做成一个服务差不多就这么简单。

现在要加上微服务一些东西。