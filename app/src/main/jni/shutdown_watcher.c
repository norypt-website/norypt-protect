/*
 * The orderly-reboot watcher (see shutdown_signal.c for why it exists). Packaged as libnorypt_watcher.so in the
 * app's native library directory (the one place an app may execute its own files) and started by the app with
 * fork + execve, so it holds none of the app's memory.
 *
 * Usage (argv set by the app only): libnorypt_watcher.so <mark file path> <record> <app pid>
 * with descriptor 3 the write end of the app's readiness pipe.
 *
 * It refuses a record that is malformed or does not name the running boot (shutdown_record.h), leads its own process
 * group (so init's DoReboot signals it in the untracked pass and waits for it, and zygote's process-group SIGKILL never
 * reaches it), opens the mark file, writes one byte to descriptor 3 and closes it (the app's proof that it is armed),
 * and waits. A SIGTERM whose sender is init (pid 1) writes the record and ends it; any other signal ends it without
 * writing. Once the app is gone (an ordinary kill of the app takes this process along: same cgroup), it ends after
 * a grace in which init's SIGTERM may still come. No output, nothing logged, no network, no other file.
 */
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <unistd.h>

#include "shutdown_record.h"

#define INIT_PID 1
#define READY_FD 3
#define READY_BYTE 'R'
#define POLL_S 5
#define GRACE_S 15

static int g_fd = -1;
static char g_record[MAX_RECORD];
static size_t g_record_len = 0;
static pid_t g_app = -1;
static volatile sig_atomic_t g_orphaned = 0;

/* A positive decimal pid without sign, spaces or leading zero; -1 otherwise. */
static pid_t parse_pid(const char *s) {
    if (s == NULL || s[0] < '1' || s[0] > '9') return -1;
    long v = 0;
    for (const char *p = s; *p != '\0'; p++) {
        if (*p < '0' || *p > '9') return -1;
        v = v * 10 + (*p - '0');
        if (v > 0x7fffffffL) return -1;
    }
    return (pid_t) v;
}

static void on_term(int sig, siginfo_t *info, void *context) {
    (void) sig;
    (void) context;
    if (info != NULL && info->si_code == SI_USER && info->si_pid == INIT_PID && g_fd >= 0) {
        if (lseek(g_fd, 0, SEEK_SET) == 0 && write(g_fd, g_record, g_record_len) == (ssize_t) g_record_len) {
            (void) ftruncate(g_fd, (off_t) g_record_len);
            (void) fsync(g_fd);
        }
    }
    _exit(0);
}

static void on_alarm(int sig) {
    (void) sig;
    if (g_orphaned) _exit(0);
    if (getppid() != g_app) {
        g_orphaned = 1;
        (void) alarm(GRACE_S);
    } else {
        (void) alarm(POLL_S);
    }
}

static void set_handler(int sig, void (*handler)(int)) {
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = handler;
    sigfillset(&sa.sa_mask);
    (void) sigaction(sig, &sa, NULL);
}

int main(int argc, char **argv) {
    sigset_t all;
    sigfillset(&all);
    (void) sigprocmask(SIG_SETMASK, &all, NULL);
    /* Descriptor 3 is ours only for the readiness byte: never inherited by anything (this process execs nothing). */
    (void) fcntl(READY_FD, F_SETFD, FD_CLOEXEC);
    if (argc != 4 || argv[1] == NULL || argv[1][0] != '/' || argv[2] == NULL) return 0;
    size_t len = strlen(argv[2]);
    g_app = parse_pid(argv[3]);
    if (len > MAX_RECORD || !record_of_this_boot(argv[2], len) || g_app <= 0) return 0;
    memcpy(g_record, argv[2], len);
    g_record_len = len;
    if (setpgid(0, 0) != 0) return 0;
    g_fd = open(argv[1], O_WRONLY | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (g_fd < 0) return 0;
    /* No other descriptor of the app is open here: every one was close-on-exec (shutdown_signal.c). */
    for (int sig = 1; sig < NSIG; sig++) {
        if (sig == SIGKILL || sig == SIGSTOP) continue;
        set_handler(sig, SIG_DFL);
    }
    set_handler(SIGPIPE, SIG_IGN);
    set_handler(SIGALRM, on_alarm);
    struct sigaction term;
    memset(&term, 0, sizeof(term));
    term.sa_sigaction = on_term;
    term.sa_flags = SA_SIGINFO;
    sigfillset(&term.sa_mask);
    if (sigaction(SIGTERM, &term, NULL) != 0) return 0;
    /* Armed: tell the app (it may have stopped waiting; a closed pipe is no error: SIGPIPE is ignored). */
    const char ready = READY_BYTE;
    while (write(READY_FD, &ready, 1) < 0 && errno == EINTR) {}
    (void) close(READY_FD);
    /* The app may already be gone: then the orphan check runs at once. */
    (void) alarm(getppid() == g_app ? POLL_S : 1);
    sigset_t none;
    sigemptyset(&none);
    (void) sigprocmask(SIG_SETMASK, &none, NULL);
    for (;;) (void) pause();
}
