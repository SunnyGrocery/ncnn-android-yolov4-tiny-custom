# ncnn-android-yolov4-tiny-custom

## Face Mask Detection

> Model trained by AlexeyAB/darknet,which implement the YOLOv4-tiny algorithm,
> Model converted and deployed by Tencent/NCNN.

### Introduction

+ APP对人脸是否佩戴口罩进行目标检测；
+ 模型在darknet上训练；
+ 数据集包含12,373 张图片，训练集与测试集比例为8:2；
+ 使用yolov4-tiny，并在[yolov4-tiny.conv.29](https://github.com/AlexeyAB/darknet/releases/download/darknet_yolo_v4_pre/yolov4-tiny.conv.29)上进行预训练；
+ 通过NCNN完成模型转换与安卓平台部署，可在Android端调用摄像头实时检测。

### How to use it?

+ Using Android Stdio to build this project.
+ You need install Android SDK , Android NDK and CMake additional in Android Stido.
### Enjoy it:

+ [releases](https://github.com/SunnyGrocery/ncnn-android-yolov4-tiny-custom/releases/latest) （In order to reduce .apk size,  apk built retain the environment of  `arm64` only.)

---

### Reference：

+ [AlexeyAB/darknet](https://github.com/AlexeyAB/darknet)
+ [dog-qiuqiu/YOLOv5_NCNN](https://github.com/dog-qiuqiu/YOLOv5_NCNN)
+ [nihui/ncnn-android-yolov5](https://github.com/nihui/ncnn-android-yolov5)

