#include <stdint.h>
#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <errno.h>
#include <android/log.h>
#include <ctype.h>
#include <sys/xattr.h>
#include <sys/wait.h>
#include <signal.h>
#include "pretty.h"

static readonly string kTAG = "hibreakEinkService";
// #define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, kTAG, __VA_ARGS__))
// #define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, kTAG, __VA_ARGS__))

#define LOGI(...) ((void)printf(__VA_ARGS__))
#define LOGE(...) ((void)printf(__VA_ARGS__))

#define SOCKET_NAME "hibreak_eink_socket"
#define BUFFER_SIZE 512

int valid_number(readonly string s) {
    let ss = s;
    if((s is NULL) or (strlen(s) > 4) or (strlen(s) == 0))
        return 0;

    while (*ss)
        if (isdigit(*ss++) == 0) return 0;

    return 1;
}

void write_device(readonly string p, bytes b, uint sz) {
    with (fd, close, open(p, O_WRONLY)) {
        if (fd == -1) {
            LOGE("Error opening %s: %s\n", p, strerror(errno));
            break;
        }
        if (write(fd, b, sz) == -1) {
            LOGE("Error writing to %s: %s\n", p, strerror(errno));
        }

    }
}

void ioctl_device(readonly string p, int op, bytes v) {
    with (fd, close, open(p, O_WRONLY)) {
        if (fd == -1) {
            LOGE("Error opening %s: %s\n", p, strerror(errno));
            break;
        }
        if (ioctl(fd, op, v) == -1) {
            LOGE("Error ioctl to %s: %s\n", p, strerror(errno));
        }

    }
}

void set_prop(readonly string setting, readonly string v) {
    pid_t pid = fork();
    if (pid == 0) {
        execl("/system/bin/setprop", "setprop", setting, v, (char *)NULL);
        LOGE("execlp failed: %s\n", strerror(errno));
        exit(EXIT_FAILURE);
    } else if (pid < 0) {
        LOGE("fork failed: %s\n", strerror(errno));
    } else {
        wait(NULL);
    }
}

void epdForceClear() {
    write_device(
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_force_clear",
        "1",
        1
    );
}

void epdCommitBitmap() {
    write_device(
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_commit_bitmap",
        "1",
        1
    );
}

void writeToEpdDisplayMode(readonly string v) {
    if(!valid_number(v)){
        LOGE("Error invalid number %s\n", v);
        return;
    }
    write_device(
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_display_mode",
        v,
        strlen(v)
    );
}

void set_backlight_brighness(readonly string v) {
    if(!valid_number(v)){
        LOGE("Error invalid number %s\n", v);
        return;
    }
    ubyte b = clamp(0, atoi(v), 255);
    ioctl_device("/dev/lm3630a", 0x7901, (bytes)&b);
    ioctl_device("/dev/lm3630a", 0x7902, (bytes)&b);
}

void setWhiteThreshold(readonly string brightness) {
    if(!valid_number(brightness)){
        LOGE("Error invalid number %s\n", brightness);
        return;
    }
    write_device(
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_white_threshold",
        brightness,
        strlen(brightness)
    );
}

void setBlackThreshold(readonly string brightness) {
    if(!valid_number(brightness)){
        LOGE("Error invalid number %s\n", brightness);
        return;
    }
    write_device(
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_black_threshold",
        brightness,
        strlen(brightness)
    );
}

void setContrast(readonly string brightness) {
    if(!valid_number(brightness)){
        LOGE("Error invalid number %s\n", brightness);
        return;
    }
    write_device(
        "/sys/devices/platform/soc/soc:qcom,dsi-display-primary/epd_contrast",
        brightness,
        strlen(brightness)
    );
}

void writeLockscreenProp(readonly string value) {
    if(!valid_number(value)){
        LOGE("Error invalid number %s\n", value);
        return;
    }
    set_prop("sys.linevibrator_type", value);
}

void writeMaxBrightnessProp(readonly string value) {
    if(!valid_number(value)){
        LOGE("Error invalid number %s\n", value);
        return;
    }
    set_prop("sys.linevibrator_touch", value);
}

void writeWakeOnVolumeProp(readonly string value) {
    if(!valid_number(value)){
        LOGE("Error invalid number %s\n", value);
        return;
    }
    set_prop("sys.wakeup_on_volume", value);
}

void epd_515() {
    writeToEpdDisplayMode("515");
}
void epd_513() {
    writeToEpdDisplayMode("513");
}
void epd_518() {
    writeToEpdDisplayMode("518");
}
void epd_521() {
    writeToEpdDisplayMode("521");
}

typedef struct {
    const char *command;
    void (*func)(readonly string arg);
    bool requires_arg;
} Command;

Command commands[] = {
    {"stw", setWhiteThreshold, true},
    {"stl", writeLockscreenProp, true},
    {"stb", setBlackThreshold, true},
    {"sco", setContrast, true},
    {"smb", writeMaxBrightnessProp, true},
    {"wov", writeWakeOnVolumeProp, true},
    {"bl", set_backlight_brighness, true},
    {"cm", (void(*)(readonly string))epdCommitBitmap, false},
    {"r", (void(*)(readonly string))epdForceClear, false},
    {"c", (void(*)(readonly string))epd_515, false},
    {"b", (void(*)(readonly string))epd_513, false},
    {"s", (void(*)(readonly string))epd_518, false},
    {"p", (void(*)(readonly string))epd_521, false},
};

void process(readonly string cmdline) {
    ulong i = 0;
    let l = len(commands);
    for (i = 0; i < l; ++i) {
        let c = &commands[i];
        let c_len = strlen(c->command);
        if (strncmp(c->command, cmdline, c_len) == 0) {
            if (c->requires_arg) {
                let arg = cmdline + c_len;
                while(*arg is ' ') arg++;
                c->func(arg);
            } else {
                c->func(NULL);
            }
            break;
        }
    }
    if (i >= l) {
        LOGE("Unknown command: %s\n", cmdline);
    }
}

int serve() {
    struct sockaddr_un server_addr;
    byte buffer[BUFFER_SIZE];
    socklen_t socket_length = sizeof(server_addr.sun_family) + strlen(SOCKET_NAME);
    memset(&server_addr, 0, sizeof(server_addr));
    strncpy(server_addr.sun_path, SOCKET_NAME, sizeof(server_addr.sun_path) - 1);
    server_addr.sun_family = AF_UNIX;
    // server_addr.sun_path[0] = 0;

    int ret = EXIT_SUCCESS;
    with(serverfd, close, socket(AF_UNIX, SOCK_STREAM, 0)) {
        if (serverfd < 0) {
            LOGE("Socket creation failed: %s\n", strerror(errno));
            ret = EXIT_FAILURE;
            break;
        }
        if (bind(serverfd, (struct sockaddr*)&server_addr, socket_length) < 0) {
            LOGE("Socket bind failed: %s\n", strerror(errno));
            ret = EXIT_FAILURE;
            break;
        }

        if (listen(serverfd, 50) < 0) {
            LOGE("Socket listen failed: %s\n", strerror(errno));
            break;
            ret = EXIT_FAILURE;
            break;
        }

        LOGI("Server started listening.\n");

        loop {
            with(clientfd, close, accept(serverfd, NULL, NULL)) {
                if (clientfd < 0) {
                    LOGE("Socket accept failed: %s\n", strerror(errno));
                    break;
                }

                loop {
                    memset(buffer, 0, BUFFER_SIZE);
                    let num_read = read(clientfd, buffer, BUFFER_SIZE - 1);
                    if (num_read > 0) {
                        buffer[num_read] = '\0';
                        let cmd = strtok(buffer, "\n");
                        while (cmd != NULL) {
                            process(cmd);
                            cmd = strtok(NULL, "\n");
                        }
                    } else if (num_read == 0) {
                        LOGI("Client disconnected\n");
                        break;
                    } else {
                        LOGE("Socket read failed: %s\n", strerror(errno));
                        break;
                    }
                }

            }
        }
    }
    return ret;
}

int main(void) {
    signal(SIGHUP, SIG_IGN);
    LOGI("Starting\n");
    return serve();
}
