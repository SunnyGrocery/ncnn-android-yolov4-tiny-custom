#ifndef YOLOV4_H
#define YOLOV4_H

#include "ncnn/net.h"

namespace cv {
    typedef struct {
        int width;
        int height;
    } Size;
}

typedef struct {
    std::string name;
    int stride;
    std::vector<cv::Size> anchors;
} YoloLayerData;

typedef struct BoxInfo {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
    int label;

} BoxInfo;

class yolov4 {
public:
    yolov4(AAssetManager *mgr, const char *param, const char *bin, bool useGPU);

    ~yolov4();

    std::vector<BoxInfo> detect(JNIEnv *env, jobject image, float threshold, float nms_threshold);

    std::vector<std::string> labels{"nomask", "masked"};
private:
    static std::vector<BoxInfo> decode_infer(ncnn::Mat &data, const cv::Size &frame_size, float threshold);

    ncnn::Net *Net;
    int input_size = 416;

    static void nms(std::vector<BoxInfo> &input_boxes, float NMS_THRESH);

public:
    static yolov4 *detector;
    static bool hasGPU;
    static bool toUseGPU;


};


#endif
