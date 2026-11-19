/*
 * Orderly-reboot mark for C9, the forced-restart trigger (fix 1.3.5).
 *
 * A reboot that init performs itself (GrapheneOS auto-reboot, `adb reboot`, a sys.powerctl set by a system daemon)
 * never runs the framework's ShutdownThread, so apps get no ACTION_SHUTDOWN. Every such reboot runs init's DoReboot:
 * before any SIGKILL it sends SIGTERM to every service process group (StopServices -> Service::Terminate) and then,
 * from pid 1, to every process group no service leads (StopUntrackedProcesses), and waits up to half the clean
 * shutdown timeout (3 s by default) for them before the SIGKILL pass. Outside DoReboot init never sends SIGTERM to a
 * process group: Service::Stop / Restart use SIGKILL unless the service is gentle_kill (zygote is not). A held power
 * button resets the phone in hardware and sends nothing.
 *
 * App processes share zygote's process group, and init SIGKILLs that whole group as soon as it reaps zygote, which
 * dies from the same SIGTERM at once: an in-process handler has only a short, unbounded window. So the mark is kept by
 * a watcher (shutdown_watcher.c, packaged as libnorypt_watcher.so): forked and exec'd by the app (so it holds none of
 * the app's memory), it leads its own process group, so init signals it in the untracked pass and waits for it, and
 * zygote's SIGKILL never reaches it. The app process keeps the same handler here as a second chance.
 *
 * Only async-signal-safe calls run in the handler and in the forked child before execve (fork from a multi-threaded
 * process). The child closes nothing (bionic's fdsan would report closing descriptors other code owns): it marks every
 * descriptor close-on-exec with fcntl, which fdsan does not check, and close_range is not used because older Android
 * seccomp filters kill a process calling it. No signal from anyone but pid 1 writes. Nothing is logged.
 */
#define _GNU_SOURCE /* pipe2 */
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <limits.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/resource.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

#include "shutdown_record.h"

#define INIT_PID 1
/* Never more descriptors are marked close-on-exec in the child than this (the fd limit is read before the fork). */
#define MAX_FD_SCAN 65536
/* The watcher's readiness pipe arrives as its descriptor 3; the app waits at most this long for the byte. */
#define READY_FD 3
#define READY_BYTE 'R'
#define READY_WAIT_MS 1500

/* Answers of nativeEnsureWatcher. */
#define WATCHER_RUNNING 0
#define WATCHER_STARTED 1
#define WATCHER_FAILED (-1)

static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
/* Under g_lock. */
static int g_installed = 0;
static pid_t g_watcher = -1;
/* Set once, before any handler is installed or any watcher forked, and never changed afterwards. */
static int g_fd = -1;
static char g_record[MAX_RECORD];
static size_t g_record_len = 0;
static struct sigaction g_previous;
/* The watcher's argv, built before any fork (the child only calls execve). */
static char g_exe[PATH_MAX];
static char g_mark[PATH_MAX];
static char g_record_arg[MAX_RECORD + 1];
static char g_pid_arg[16];
static char *g_argv[5];
static char *g_envp[1];

static int from_init(const siginfo_t *info) {
    return info != NULL && info->si_code == SI_USER && info->si_pid == INIT_PID;
}

/* Async-signal-safe. */
static void write_mark(void) {
    if (g_fd < 0 || g_record_len == 0) return;
    if (lseek(g_fd, 0, SEEK_SET) == 0 && write(g_fd, g_record, g_record_len) == (ssize_t) g_record_len) {
        (void) ftruncate(g_fd, (off_t) g_record_len);
        (void) fsync(g_fd);
    }
}

/* ── the app process: second chance ── */

static void on_sigterm(int sig, siginfo_t *info, void *context) {
    int saved_errno = errno;
    if (from_init(info)) write_mark();
    /* Hand the signal on as if this handler had never been installed. */
    if ((g_previous.sa_flags & SA_SIGINFO) != 0) {
        if (g_previous.sa_sigaction != NULL) g_previous.sa_sigaction(sig, info, context);
    } else if (g_previous.sa_handler == SIG_IGN) {
        /* Ignored before: still ignored. */
    } else if (g_previous.sa_handler != SIG_DFL) {
        g_previous.sa_handler(sig);
    } else {
        struct sigaction dfl;
        memset(&dfl, 0, sizeof(dfl));
        dfl.sa_handler = SIG_DFL;
        sigemptyset(&dfl.sa_mask);
        (void) sigaction(sig, &dfl, NULL);
        /* SIGTERM is blocked while this handler runs: it stays pending and the default action ends the process as
         * soon as the handler returns. */
        (void) raise(sig);
    }
    errno = saved_errno;
}

static long now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long) ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}

/*
 * Waits for the watcher's readiness byte on [fd] (at most READY_WAIT_MS in all). 1 the byte came (armed), 0 the pipe
 * closed without it (exec failed, or the watcher refused and ended), -1 no answer in time (still starting: the next
 * nativeEnsureWatcher sees whether it lives).
 */
static int await_ready(int fd) {
    long deadline = now_ms() + READY_WAIT_MS;
    for (;;) {
        long left = deadline - now_ms();
        if (left <= 0) return -1;
        struct pollfd p = { .fd = fd, .events = POLLIN, .revents = 0 };
        int r = poll(&p, 1, (int) left);
        if (r < 0 && errno == EINTR) continue;
        if (r <= 0) return -1;
        char c = 0;
        ssize_t n = read(fd, &c, 1);
        if (n < 0 && errno == EINTR) continue;
        return n == 1 && c == READY_BYTE ? 1 : 0;
    }
}

/*
 * Under g_lock, after arming. Forks and execs a watcher. WATCHER_STARTED when it reported armed (or did not answer in
 * time and still runs), WATCHER_FAILED when it could not be forked or exec'd or refused to arm (it is reaped then).
 */
static int start_watcher_locked(void) {
    struct rlimit rl;
    int max_fd = MAX_FD_SCAN;
    if (getrlimit(RLIMIT_NOFILE, &rl) == 0 && rl.rlim_cur != RLIM_INFINITY && rl.rlim_cur < (rlim_t) MAX_FD_SCAN) {
        max_fd = (int) rl.rlim_cur;
    }
    int n = snprintf(g_pid_arg, sizeof(g_pid_arg), "%d", (int) getpid());
    if (n <= 0 || (size_t) n >= sizeof(g_pid_arg)) return WATCHER_FAILED;
    g_argv[0] = g_exe;
    g_argv[1] = g_mark;
    g_argv[2] = g_record_arg;
    g_argv[3] = g_pid_arg;
    g_argv[4] = NULL;
    g_envp[0] = NULL;
    int ready[2];
    if (pipe2(ready, O_CLOEXEC) != 0) return WATCHER_FAILED;
    pid_t pid = fork();
    if (pid == 0) {
        /* Child of a multi-threaded process: async-signal-safe calls only, nothing closed (fdsan). Descriptors 0-2
         * (zygote's /dev/null) stay, so nothing the watcher opens can take their place and catch a libc message; the
         * readiness pipe's write end becomes descriptor 3; every other one is close-on-exec. */
        int w = ready[1];
        if (w == READY_FD) {
            if (fcntl(READY_FD, F_SETFD, 0) != 0) _exit(127);
        } else if (dup2(w, READY_FD) != READY_FD) {
            _exit(127);
        }
        for (int fd = READY_FD + 1; fd < max_fd; fd++) {
            int flags = fcntl(fd, F_GETFD);
            if (flags >= 0 && (flags & FD_CLOEXEC) == 0) (void) fcntl(fd, F_SETFD, flags | FD_CLOEXEC);
        }
        execve(g_exe, g_argv, g_envp);
        _exit(127);
    }
    (void) close(ready[1]);
    if (pid < 0) {
        (void) close(ready[0]);
        return WATCHER_FAILED;
    }
    int answer = await_ready(ready[0]);
    (void) close(ready[0]);
    if (answer == 0) {
        /* Ended without arming: reap it (it exits at once; never wait on a live one). */
        int status = 0;
        pid_t r;
        do {
            r = waitpid(pid, &status, 0);
        } while (r < 0 && errno == EINTR);
        return WATCHER_FAILED;
    }
    g_watcher = pid;
    return WATCHER_STARTED;
}

/* Under g_lock. */
static int ensure_watcher_locked(void) {
    if (!g_installed) return WATCHER_FAILED;
    if (g_watcher > 0) {
        int status = 0;
        pid_t r = waitpid(g_watcher, &status, WNOHANG);
        if (r == 0) return WATCHER_RUNNING;
        /* Gone (reaped now, or already reaped elsewhere): start a new one. */
        g_watcher = -1;
    }
    return start_watcher_locked();
}

/*
 * Arms once per process: opens [jpath] (created 0600 if missing, never truncated here, never following a symlink),
 * installs the app's SIGTERM handler and keeps the watcher's path [jexe] (absolute, in the app's native library
 * directory) and argv; nativeEnsureWatcher then starts it and reports. A later call changes nothing and answers true.
 * False when an argument is malformed, the record does not name the running boot (shutdown_record.h), the file cannot
 * be opened or sigaction fails (then nothing is armed).
 */
JNIEXPORT jboolean JNICALL
Java_com_norypt_protect_triggers_ShutdownSignalNative_nativeArm(JNIEnv *env, jclass clazz, jstring jpath,
                                                                jstring jrecord, jstring jexe) {
    (void) clazz;
    if (jpath == NULL || jrecord == NULL || jexe == NULL) return JNI_FALSE;
    jboolean ok = JNI_FALSE;
    pthread_mutex_lock(&g_lock);
    if (g_installed) {
        pthread_mutex_unlock(&g_lock);
        return JNI_TRUE;
    }
    const char *path = (*env)->GetStringUTFChars(env, jpath, NULL);
    const char *record = (*env)->GetStringUTFChars(env, jrecord, NULL);
    const char *exe = (*env)->GetStringUTFChars(env, jexe, NULL);
    if (path != NULL && record != NULL && exe != NULL) {
        size_t len = strlen(record);
        size_t path_len = strlen(path);
        size_t exe_len = strlen(exe);
        if (len <= MAX_RECORD && record_of_this_boot(record, len) && path[0] == '/' && path_len < sizeof(g_mark) &&
                exe[0] == '/' && exe_len < sizeof(g_exe)) {
            memcpy(g_mark, path, path_len + 1);
            memcpy(g_exe, exe, exe_len + 1);
            memcpy(g_record_arg, record, len + 1);
            int fd = open(path, O_WRONLY | O_CREAT | O_CLOEXEC | O_NOFOLLOW, 0600);
            if (fd >= 0) {
                memcpy(g_record, record, len);
                g_record_len = len;
                g_fd = fd;
                struct sigaction sa;
                memset(&sa, 0, sizeof(sa));
                sa.sa_sigaction = on_sigterm;
                sa.sa_flags = SA_SIGINFO | SA_RESTART;
                sigemptyset(&sa.sa_mask);
                if (sigaction(SIGTERM, &sa, &g_previous) == 0) {
                    g_installed = 1;
                    ok = JNI_TRUE;
                } else {
                    g_fd = -1;
                    g_record_len = 0;
                    close(fd);
                }
            }
        }
    }
    if (exe != NULL) (*env)->ReleaseStringUTFChars(env, jexe, exe);
    if (record != NULL) (*env)->ReleaseStringUTFChars(env, jrecord, record);
    if (path != NULL) (*env)->ReleaseStringUTFChars(env, jpath, path);
    pthread_mutex_unlock(&g_lock);
    return ok;
}

/* The watcher runs (0), was not running and is started (1), or there is none and none could be started (-1). */
JNIEXPORT jint JNICALL
Java_com_norypt_protect_triggers_ShutdownSignalNative_nativeEnsureWatcher(JNIEnv *env, jclass clazz) {
    (void) env;
    (void) clazz;
    pthread_mutex_lock(&g_lock);
    int r = ensure_watcher_locked();
    pthread_mutex_unlock(&g_lock);
    return r;
}
