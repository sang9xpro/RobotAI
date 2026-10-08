#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <arm_neon.h>
extern "C" {
void *sea_g2p_open(const char*);
void sea_g2p_close(void*);
char *sea_g2p_phonemize(const void*, const char*, int);
char *sea_g2p_normalize(const void*, const char*, int);
void sea_g2p_string_free(char*);
const char *sea_g2p_last_error();
}
static void fail(JNIEnv *e, const char *msg) {
    e->ThrowNew(e->FindClass("java/lang/IllegalStateException"), msg ? msg : "sea-g2p error");
}
extern "C" JNIEXPORT jlong JNICALL Java_com_whispercppdemo_tts_NativeTts_open(JNIEnv* e,jobject,jstring path) {
    auto p=e->GetStringUTFChars(path,nullptr);auto h=sea_g2p_open(p);e->ReleaseStringUTFChars(path,p);
    if(!h)fail(e,sea_g2p_last_error());return reinterpret_cast<jlong>(h);
}
static jbyteArray text(JNIEnv* e,jlong h,jbyteArray input,bool normalize) {
    const int n=e->GetArrayLength(input);std::string s(n,'\0');
    e->GetByteArrayRegion(input,0,n,reinterpret_cast<jbyte*>(s.data()));
    char *p=normalize?sea_g2p_normalize(reinterpret_cast<void*>(h),s.c_str(),1):sea_g2p_phonemize(reinterpret_cast<void*>(h),s.c_str(),1);
    if(!p){fail(e,sea_g2p_last_error());return nullptr;}
    int size=std::strlen(p);auto out=e->NewByteArray(size);e->SetByteArrayRegion(out,0,size,reinterpret_cast<jbyte*>(p));sea_g2p_string_free(p);return out;
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_whispercppdemo_tts_NativeTts_phonemize(JNIEnv* e,jobject,jlong h,jbyteArray t){return text(e,h,t,false);}
extern "C" JNIEXPORT jbyteArray JNICALL Java_com_whispercppdemo_tts_NativeTts_normalize(JNIEnv* e,jobject,jlong h,jbyteArray t){return text(e,h,t,true);}
extern "C" JNIEXPORT void JNICALL Java_com_whispercppdemo_tts_NativeTts_close(JNIEnv*,jobject,jlong h){sea_g2p_close(reinterpret_cast<void*>(h));}
extern "C" JNIEXPORT jfloatArray JNICALL Java_com_whispercppdemo_tts_NativeTts_logits(JNIEnv* e,jobject,jfloatArray matrix,jint offset,jint rows,jfloatArray vector) {
    const int cols=e->GetArrayLength(vector);
    if(offset<0||rows<1||static_cast<long long>(offset)+static_cast<long long>(rows)*cols>e->GetArrayLength(matrix)){fail(e,"Invalid projection dimensions");return nullptr;}
    std::vector<float> result(rows);
    auto m=static_cast<float*>(e->GetPrimitiveArrayCritical(matrix,nullptr));
    auto v=static_cast<float*>(e->GetPrimitiveArrayCritical(vector,nullptr));
    for(int r=0;r<rows;r++) {
        const float* a=m+offset+r*cols;float32x4_t sum=vdupq_n_f32(0);int c=0;
        for(;c+4<=cols;c+=4)sum=vfmaq_f32(sum,vld1q_f32(a+c),vld1q_f32(v+c));
        float value=vaddvq_f32(sum);for(;c<cols;c++)value+=a[c]*v[c];result[r]=value;
    }
    e->ReleasePrimitiveArrayCritical(vector,v,JNI_ABORT);e->ReleasePrimitiveArrayCritical(matrix,m,JNI_ABORT);
    auto out=e->NewFloatArray(rows);e->SetFloatArrayRegion(out,0,rows,result.data());return out;
}
