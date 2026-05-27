#include <jni.h>
#include <vterm.h>

#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

namespace {

struct Terminal {
    VTerm *vt = nullptr;
    VTermScreen *screen = nullptr;
    int rows = 0;
    int cols = 0;
    std::mutex lock;
};

Terminal *from_ptr(jlong ptr) {
    return reinterpret_cast<Terminal *>(ptr);
}

int screen_damage(VTermRect, void *) {
    return 1;
}

int screen_moverect(VTermRect, VTermRect, void *) {
    return 1;
}

int screen_movecursor(VTermPos, VTermPos, int, void *) {
    return 1;
}

int screen_settermprop(VTermProp, VTermValue *, void *) {
    return 1;
}

int screen_bell(void *) {
    return 1;
}

int screen_resize(int, int, void *) {
    return 1;
}

int screen_pushline(int, const VTermScreenCell *, void *) {
    return 1;
}

int screen_popline(int, VTermScreenCell *, void *) {
    return 0;
}

int screen_clear(void *) {
    return 1;
}

const VTermScreenCallbacks SCREEN_CALLBACKS = {
        screen_damage,
        screen_moverect,
        screen_movecursor,
        screen_settermprop,
        screen_bell,
        screen_resize,
        screen_pushline,
        screen_popline,
        screen_clear,
        nullptr
};

void flush(Terminal *terminal) {
    if (terminal != nullptr && terminal->screen != nullptr) {
        vterm_screen_flush_damage(terminal->screen);
    }
}

Terminal *create_terminal(int rows, int cols) {
    rows = std::max(1, rows);
    cols = std::max(1, cols);
    auto *terminal = new Terminal();
    terminal->rows = rows;
    terminal->cols = cols;
    terminal->vt = vterm_new(rows, cols);
    vterm_set_utf8(terminal->vt, 1);
    terminal->screen = vterm_obtain_screen(terminal->vt);
    vterm_screen_set_callbacks(terminal->screen, &SCREEN_CALLBACKS, terminal);
    vterm_screen_enable_altscreen(terminal->screen, 1);
    vterm_screen_enable_reflow(terminal->screen, false);
    vterm_screen_set_damage_merge(terminal->screen, VTERM_DAMAGE_ROW);
    vterm_screen_reset(terminal->screen, 1);
    return terminal;
}

std::string row_text(Terminal *terminal, int row) {
    if (terminal == nullptr || terminal->screen == nullptr || row < 0 || row >= terminal->rows) {
        return {};
    }

    std::vector<char> buffer(static_cast<size_t>(terminal->cols) * 8U + 8U);
    VTermRect rect{row, row + 1, 0, terminal->cols};
    size_t len = vterm_screen_get_text(terminal->screen, buffer.data(), buffer.size(), rect);
    while (len > 0 && (buffer[len - 1] == ' ' || buffer[len - 1] == '\0')) {
        --len;
    }
    return std::string(buffer.data(), len);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeCreate(JNIEnv *, jobject, jint rows, jint cols) {
    return reinterpret_cast<jlong>(create_terminal(rows, cols));
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeDestroy(JNIEnv *, jobject, jlong ptr) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        return;
    }
    {
        std::lock_guard<std::mutex> guard(terminal->lock);
        if (terminal->vt != nullptr) {
            vterm_free(terminal->vt);
            terminal->vt = nullptr;
            terminal->screen = nullptr;
        }
    }
    delete terminal;
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeReset(JNIEnv *, jobject, jlong ptr, jint rows, jint cols) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> guard(terminal->lock);
    rows = std::max(1, rows);
    cols = std::max(1, cols);
    terminal->rows = rows;
    terminal->cols = cols;
    vterm_set_size(terminal->vt, rows, cols);
    vterm_screen_reset(terminal->screen, 1);
    flush(terminal);
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeResize(JNIEnv *, jobject, jlong ptr, jint rows, jint cols) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        return;
    }
    std::lock_guard<std::mutex> guard(terminal->lock);
    rows = std::max(1, rows);
    cols = std::max(1, cols);
    if (terminal->rows == rows && terminal->cols == cols) {
        return;
    }
    terminal->rows = rows;
    terminal->cols = cols;
    vterm_set_size(terminal->vt, rows, cols);
    flush(terminal);
}

extern "C" JNIEXPORT void JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeWrite(JNIEnv *env, jobject, jlong ptr, jbyteArray data) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr || data == nullptr) {
        return;
    }
    jsize len = env->GetArrayLength(data);
    if (len <= 0) {
        return;
    }
    std::vector<char> bytes(static_cast<size_t>(len));
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte *>(bytes.data()));
    std::lock_guard<std::mutex> guard(terminal->lock);
    vterm_input_write(terminal->vt, bytes.data(), static_cast<size_t>(len));
    flush(terminal);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeLines(JNIEnv *env, jobject, jlong ptr) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        jclass stringClass = env->FindClass("java/lang/String");
        return env->NewObjectArray(0, stringClass, nullptr);
    }

    std::vector<std::string> lines;
    {
        std::lock_guard<std::mutex> guard(terminal->lock);
        flush(terminal);
        lines.reserve(static_cast<size_t>(terminal->rows));
        for (int row = 0; row < terminal->rows; ++row) {
            lines.push_back(row_text(terminal, row));
        }
    }

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(lines.size()), stringClass, nullptr);
    for (size_t i = 0; i < lines.size(); ++i) {
        jstring value = env->NewStringUTF(lines[i].c_str());
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
    }
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeCursorRow(JNIEnv *, jobject, jlong ptr) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        return 0;
    }
    std::lock_guard<std::mutex> guard(terminal->lock);
    VTermPos pos{0, 0};
    vterm_state_get_cursorpos(vterm_obtain_state(terminal->vt), &pos);
    return std::max(0, std::min(pos.row, terminal->rows - 1));
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeCursorCol(JNIEnv *, jobject, jlong ptr) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        return 0;
    }
    std::lock_guard<std::mutex> guard(terminal->lock);
    VTermPos pos{0, 0};
    vterm_state_get_cursorpos(vterm_obtain_state(terminal->vt), &pos);
    return std::max(0, std::min(pos.col, terminal->cols - 1));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wxalh_airan_1desk_terminal_NativeTerminalView_nativeCursorVisible(JNIEnv *, jobject, jlong ptr) {
    auto *terminal = from_ptr(ptr);
    if (terminal == nullptr) {
        return JNI_FALSE;
    }
    std::lock_guard<std::mutex> guard(terminal->lock);
    VTermValue value{};
    if (vterm_state_get_penattr(vterm_obtain_state(terminal->vt), VTERM_ATTR_CONCEAL, &value)) {
        return value.boolean ? JNI_FALSE : JNI_TRUE;
    }
    return JNI_TRUE;
}
