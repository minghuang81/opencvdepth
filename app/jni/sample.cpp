#include <jni.h>
#include <string>
#include <opencv2/core.hpp>
#include <opencv/cv.hpp>

extern "C"
{
    JNIEXPORT jstring JNICALL
    Java_com_example_opencvdepth_MainActivity_version(
            JNIEnv *env,
            jobject) {
        std::string version = cv::getVersionString();
        return env->NewStringUTF(version.c_str());
    }

    /**
     * This is the original open-cv-sample code that illustrates the necessary steps for
     * Android java - native code integration.
     */
    JNIEXPORT jbyteArray
    JNICALL Java_com_example_opencvdepth_MainActivity_rgba2bgra
            (
                    JNIEnv *env,
                    jobject obj,
                    jint w,
                    jint h,
                    jbyteArray src
            ) {
        // Obtaining element row
        // Need to release at the end
        jbyte *p_src = env->GetByteArrayElements(src, NULL);
        if (p_src == NULL) {
            return NULL;
        }

        // Convert arrangement to cv::Mat
        cv::Mat m_src(h, w, CV_8UC4, (u_char *) p_src);
        cv::Mat m_dst(h, w, CV_8UC4);

        // OpenCV process
        //cv::cvtColor(m_src, m_dst, CV_RGBA2BGRA);
        cv::flip(m_src, m_dst, 0); // flipCode 0 means flipping around the x-axis

        // Pick out arrangement from cv::Mat
        u_char *p_dst = m_dst.data;

        // Assign element for return value use
        jbyteArray dst = env->NewByteArray(w * h * 4);
        if (dst == NULL) {
            env->ReleaseByteArrayElements(src, p_src, 0);
            return NULL;
        }
        env->SetByteArrayRegion(dst, 0, w * h * 4, (jbyte *) p_dst);

        // release
        env->ReleaseByteArrayElements(src, p_src, 0);
        return dst;
    }

    // Convert 4-channel RGBA to 1-channel grayscale for openCV depth-map calculation
    JNIEXPORT jbyteArray
    JNICALL Java_com_example_opencvdepth_MainActivity_rgba2gray
            (
                    JNIEnv *env,
                    jobject obj,
                    jint w,
                    jint h,
                    jbyteArray src
            ) {
        // Obtaining element row
        // Need to release at the end
        jbyte *p_src = env->GetByteArrayElements(src, NULL);
        if (p_src == NULL) {
            return NULL;
        }

        // Convert arrangement to cv::Mat
        cv::Mat m_src(h, w, CV_8UC4, (u_char *) p_src);
        cv::Mat m_dst(h, w, CV_8UC1);

        // Convert to greyscale
        cv::cvtColor(m_src, m_dst, CV_RGBA2GRAY);
        // Pick out arrangement from cv::Mat
        u_char *p_dst = m_dst.data;
        // Assign element for return value use
        jbyteArray dst = env->NewByteArray(w * h * 1);
        if (dst == NULL) {
            env->ReleaseByteArrayElements(src, p_src, 0);
            return NULL;
        }
        env->SetByteArrayRegion(dst, 0, w * h * 1, (jbyte *) p_dst);

        // release
        env->ReleaseByteArrayElements(src, p_src, 0);
        return dst;
    }

    // Convert 1-channel grayscale back to 4-channel RGBA for display by normal picture viewers
    JNIEXPORT jbyteArray
    JNICALL Java_com_example_opencvdepth_MainActivity_gray2rgba
            (
                    JNIEnv *env,
                    jobject obj,
                    jint w,
                    jint h,
                    jbyteArray src
            ) {
        // Obtaining element row
        // Need to release at the end
        jbyte *p_src = env->GetByteArrayElements(src, NULL);
        if (p_src == NULL) {
            return NULL;
        }

        // Convert arrangement to cv::Mat
        cv::Mat m_src(h, w, CV_8UC1, (u_char *) p_src);
        cv::Mat m_dst(h, w, CV_8UC4);

        // Convert to greyscale
        cv::cvtColor(m_src, m_dst, CV_GRAY2RGBA);
        // Pick out arrangement from cv::Mat
        u_char *p_dst = m_dst.data;
        // Assign element for return value use
        jbyteArray dst = env->NewByteArray(w * h * 4);
        if (dst == NULL) {
            env->ReleaseByteArrayElements(src, p_src, 0);
            return NULL;
        }
        env->SetByteArrayRegion(dst, 0, w * h * 4, (jbyte *) p_dst);

        // release
        env->ReleaseByteArrayElements(src, p_src, 0);
        return dst;
    }

    // Calculate depth-map from two stereo images
    JNIEXPORT jbyteArray
    JNICALL Java_com_example_opencvdepth_MainActivity_disparity
            (
                    JNIEnv *env,
                    jobject obj,
                    jint w,
                    jint h,
                    jbyteArray srcL,
                    jbyteArray srcR
            ) {
        // Obtaining element row
        // Need to release at the end
        jbyte *p_srcL = env->GetByteArrayElements(srcL, NULL);
        if (p_srcL == NULL) {
            return NULL;
        }
        jbyte *p_srcR = env->GetByteArrayElements(srcR, NULL);
        if (p_srcR == NULL) {
            env->ReleaseByteArrayElements(srcL, p_srcL, 0);
            return NULL;
        }

        // Convert arrangement to cv::Mat
        cv::Mat m_srcL(h, w, CV_8UC1, (u_char *) p_srcL);
        cv::Mat m_srcR(h, w, CV_8UC1, (u_char *) p_srcR);
        cv::Mat m_dst(h, w, CV_8UC1);

        // OpenCV library
        static cv::Ptr<cv::StereoSGBM> stereo = cv::StereoSGBM::create(
                0,     //minDisparity
                64, //numDisparities multiple of 16
                5,      //blockSize
                200,    //P1,
                800,   //P2
                200,    //disp12MaxDiff,
                0,      //preFilterCap
                10,      //uniquenessRatio,
                50,      //speckleWindowSize,
                2,      //speckleRange,
                //cv::StereoSGBM::MODE_HH
                cv::StereoSGBM::MODE_HH4
        );
        stereo->compute(m_srcL, m_srcR, m_dst);

        // Pick out arrangement from cv::Mat
        u_char *p_dst = m_dst.data;

        // Assign element for return value use
        jbyteArray dst = env->NewByteArray(w * h * 1);
        if (dst == NULL) {
            env->ReleaseByteArrayElements(srcL, p_srcL, 0);
            env->ReleaseByteArrayElements(srcL, p_srcR, 0);
            return NULL;
        }
        env->SetByteArrayRegion(dst, 0, w * h * 1, (jbyte *) p_dst);

        // release
        env->ReleaseByteArrayElements(srcL, p_srcL, 0);
        env->ReleaseByteArrayElements(srcR, p_srcR, 0);

        return dst;
    }

}