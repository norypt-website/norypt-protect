/*
 * The orderly-reboot record, shared by shutdown_signal.c (app process) and shutdown_watcher.c (watcher):
 *
 *     <boot count> ' ' <kernel boot_id> '\n'
 *
 * The boot count (Settings.Global.BOOT_COUNT: decimal, no sign, no leading zero, at most INT_MAX) is what the
 * forced-restart rule (C9) compares; the boot_id (/proc/sys/kernel/random/boot_id: 36 lowercase hex digits and dashes, new
 * at every kernel boot, readable by every domain) proves which boot wrote it. A record is only armed when its boot_id is
 * the running kernel's, so a mark always names the boot that wrote it, whatever the boot count said.
 * Kept in step with ShutdownSignalRules.kt.
 */
#ifndef NORYPT_SHUTDOWN_RECORD_H
#define NORYPT_SHUTDOWN_RECORD_H

#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <unistd.h>

#define BOOT_ID_LEN 36
#define MAX_COUNT_DIGITS 10
/* 10 digits, the space, the boot_id, the newline. */
#define MAX_RECORD (MAX_COUNT_DIGITS + 1 + BOOT_ID_LEN + 1)
#define BOOT_ID_PATH "/proc/sys/kernel/random/boot_id"

static inline int valid_boot_id(const char *s) {
    for (int i = 0; i < BOOT_ID_LEN; i++) {
        char c = s[i];
        if (i == 8 || i == 13 || i == 18 || i == 23) {
            if (c != '-') return 0;
        } else if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
            return 0;
        }
    }
    return 1;
}

/* Length of the boot count part when [record] (of [len] bytes, no NUL needed) is well formed; 0 otherwise. */
static inline size_t record_count_len(const char *record, size_t len) {
    if (len < 1 + 1 + BOOT_ID_LEN + 1 || len > MAX_RECORD || record[len - 1] != '\n') return 0;
    size_t digits = len - (1 + BOOT_ID_LEN + 1);
    if (digits < 1 || digits > MAX_COUNT_DIGITS || record[0] == '0' || record[digits] != ' ') return 0;
    for (size_t i = 0; i < digits; i++) {
        if (record[i] < '0' || record[i] > '9') return 0;
    }
    if (digits == MAX_COUNT_DIGITS && memcmp(record, "2147483647", MAX_COUNT_DIGITS) > 0) return 0;
    return valid_boot_id(record + digits + 1) ? digits : 0;
}

/* The running kernel's boot_id into [out] (BOOT_ID_LEN bytes, no NUL); 1 on success. Not for signal handlers. */
static inline int read_boot_id(char *out) {
    int fd = open(BOOT_ID_PATH, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return 0;
    char buf[BOOT_ID_LEN + 2];
    size_t n = 0;
    while (n < sizeof(buf)) {
        ssize_t r = read(fd, buf + n, sizeof(buf) - n);
        if (r < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (r == 0) break;
        n += (size_t) r;
    }
    close(fd);
    if (n != BOOT_ID_LEN + 1 || buf[BOOT_ID_LEN] != '\n' || !valid_boot_id(buf)) return 0;
    memcpy(out, buf, BOOT_ID_LEN);
    return 1;
}

/* Well formed and naming the running boot: what the app and the watcher both require before arming. */
static inline int record_of_this_boot(const char *record, size_t len) {
    size_t digits = record_count_len(record, len);
    if (digits == 0) return 0;
    char now[BOOT_ID_LEN];
    return read_boot_id(now) && memcmp(record + digits + 1, now, BOOT_ID_LEN) == 0;
}

#endif
