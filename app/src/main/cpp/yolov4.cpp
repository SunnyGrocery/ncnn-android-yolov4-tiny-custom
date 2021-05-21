#include "yolov4.h"

bool yolov4::hasGPU = false;
bool yolov4::toUseGPU = false;
yolov4 *yolov4::detector = nullptr;

yolov4::yolov4(AAssetManager *mgr, const char *param, const char *bin, bool useGPU) {
    hasGPU = ncnn::get_gpu_count() > 0;
    toUseGPU = hasGPU && useGPU;

    Net = new ncnn::Net();

    Net->opt.use_vulkan_compute = toUseGPU;  // GPU
    Net->opt.use_fp16_arithmetic = true;  // FP16运算加速
    Net->load_param(mgr, param);
    Net->load_model(mgr, bin);
}

yolov4::~yolov4() {
    Net->clear();
    delete Net;
}

std::vector<BoxInfo>
yolov4::detect(JNIEnv *env, jobject image, float threshold, float nms_threshold) {
    AndroidBitmapInfo img_size;
    AndroidBitmap_getInfo(env, image, &img_size);
    ncnn::Mat in_net = ncnn::Mat::from_android_bitmap_resize(env, image, ncnn::Mat::PIXEL_RGBA2RGB,
                                                             input_size,
                                                             input_size);
    float norm[3] = {1 / 255.f, 1 / 255.f, 1 / 255.f};
    float mean[3] = {0, 0, 0};
    in_net.substract_mean_normalize(mean, norm);
    auto ex = Net->create_extractor();
    ex.set_light_mode(true);
    ex.set_num_threads(4);
    ex.input(0, in_net);
    std::vector<BoxInfo> result;
    ncnn::Mat blob;
    ex.extract("output", blob);
    auto boxes = decode_infer(blob, {(int) img_size.width, (int) img_size.height}, threshold);
    result.insert(result.begin(), boxes.begin(), boxes.end());
    nms(result, nms_threshold);
    return result;
}
/**
 * 从Mat->data值求bbox的实际参数值
 * @param data
 * @param frame_size
 * @param threshold
 * @return
 */
std::vector<BoxInfo>
yolov4::decode_infer(ncnn::Mat &data, const cv::Size &frame_size, float threshold) {
    std::vector<BoxInfo> result;
    for (int i = 0; i < data.h; i++) {
        BoxInfo box;
        const float *values = data.row(i);
        box.x1 = values[2] * (float) frame_size.width;
        box.y1 = values[3] * (float) frame_size.height;
        box.x2 = values[4] * (float) frame_size.width;
        box.y2 = values[5] * (float) frame_size.height;
        box.label = (int) values[0] - 1;
        box.score = values[1];
        if (box.score < threshold) {
            continue;
        }
        result.push_back(box);

    }
    return result;
}

void yolov4::nms(std::vector<BoxInfo> &input_boxes, float NMS_THRESH) {
    std::sort(input_boxes.begin(), input_boxes.end(),
              [](BoxInfo a, BoxInfo b) { return a.score > b.score; });
    std::vector<float> vArea(input_boxes.size());
    for (int i = 0; i < int(input_boxes.size()); ++i) {
        vArea[i] = (input_boxes.at(i).x2 - input_boxes.at(i).x1 + 1)
                   * (input_boxes.at(i).y2 - input_boxes.at(i).y1 + 1);
    }
    for (int i = 0; i < int(input_boxes.size()); ++i) {
        for (int j = i + 1; j < int(input_boxes.size());) {
            float xx1 = std::max(input_boxes[i].x1, input_boxes[j].x1);
            float yy1 = std::max(input_boxes[i].y1, input_boxes[j].y1);
            float xx2 = std::min(input_boxes[i].x2, input_boxes[j].x2);
            float yy2 = std::min(input_boxes[i].y2, input_boxes[j].y2);
            float w = std::max(float(0), xx2 - xx1 + 1);
            float h = std::max(float(0), yy2 - yy1 + 1);
            float inter = w * h;
            float ovr = inter / (vArea[i] + vArea[j] - inter);
            if (ovr >= NMS_THRESH) {
                input_boxes.erase(input_boxes.begin() + j);
                vArea.erase(vArea.begin() + j);
            } else {
                j++;
            }
        }
    }
}