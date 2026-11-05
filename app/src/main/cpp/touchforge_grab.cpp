// TouchForge original native input grabber.
// Opens an evdev node, takes exclusive access via EVIOCGRAB, and surfaces raw
// event bytes to Kotlin. OEM-dependent (input group membership varies);
// Kotlin side degrades gracefully when unavailable.
#include <jni.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <string>
#include <atomic>
#include <dirent.h>
#include <sys/ioctl.h>
#include <linux/input.h>

namespace {

std::atomic<int> g_fd{-1};
std::atomic<bool> g_grabbing{false};

bool ioctlGrab(int fd, bool grab) {
    return ioctl(fd, EVIOCGRAB, static_cast<void*>(grab ? 1 : 0)) == 0;
}

std::string deviceName(int fd) {
    char buf[256] = {0};
    if (ioctl(fd, EVIOCGNAME(sizeof(buf) - 1), buf) < 0) return "";
    buf[sizeof(buf) - 1] = '\0';
    return std::string(buf);
}

} // namespace

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_touchforge_toolkit_helper_NativeGrabber_nativeProbe(JNIEnv* env, jobject /*thiz*/) {
    std::string out;
    DIR* dir = opendir("/dev/input");
    if (dir == nullptr) return env->NewStringUTF("");
    dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        std::string path = std::string("/dev/input/") + entry->d_name;
        int fd = open(path.c_str(), O_RDONLY);
        if (fd < 0) continue;
        std::string name = deviceName(fd);
        close(fd);
        out += path;
        out += '\t';
        out += name;
        out += '\n';
    }
    closedir(dir);
    return env->NewStringUTF(out.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_touchforge_toolkit_helper_NativeGrabber_nativeStartGrab(JNIEnv* env, jobject /*thiz*/, jstring jPath) {
    if (g_grabbing.load()) return JNI_TRUE;
    const char* path = env->GetStringUTFChars(jPath, nullptr);
    if (path == nullptr) return JNI_FALSE;
    int fd = open(path, O_RDONLY);
    env->ReleaseStringUTFChars(jPath, path);
    if (fd < 0) return JNI_FALSE;
    if (!ioctlGrab(fd, true)) {
        close(fd);
        return JNI_FALSE;
    }
    g_fd.store(fd);
    g_grabbing.store(true);
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_touchforge_toolkit_helper_NativeGrabber_nativeRead(JNIEnv* env, jobject /*thiz*/, jint maxBytes) {
    int fd = g_fd.load();
    if (fd < 0 || !g_grabbing.load()) return env->NewByteArray(0);
    int want = maxBytes < 64 ? 64 : (maxBytes > 65536 ? 65536 : maxBytes);
    // evdev events are 24 bytes on 64-bit; read whole events only.
    want -= want % static_cast<int>(sizeof(input_event));
    auto* buf = new uint8_t[want];
    ssize_t n = ::read(fd, buf, static_cast<size_t>(want));
    if (n <= 0) {
        delete[] buf;
        return env->NewByteArray(0);
    }
    jbyteArray arr = env->NewByteArray(static_cast<jsize>(n));
    env->SetByteArrayRegion(arr, 0, static_cast<jsize>(n), reinterpret_cast<const jbyte*>(buf));
    delete[] buf;
    return arr;
}

JNIEXPORT void JNICALL
Java_com_touchforge_toolkit_helper_NativeGrabber_nativeStopGrab(JNIEnv* /*env*/, jobject /*thiz*/) {
    int fd = g_fd.exchange(-1);
    g_grabbing.store(false);
    if (fd >= 0) {
        ioctlGrab(fd, false);
        close(fd);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_touchforge_toolkit_helper_NativeGrabber_nativeIsGrabbing(JNIEnv* /*env*/, jobject /*thiz*/) {
    return g_grabbing.load() ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
