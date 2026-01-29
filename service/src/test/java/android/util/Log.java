package android.util;

public class Log {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int ASSERT = 7;

    public static boolean isLoggableEnabled = true;
    public static boolean printToStdout = true;

    public static int d(String tag, String msg) { if (printToStdout) System.out.println("D/" + tag + ": " + msg); return 0; }
    public static int i(String tag, String msg) { if (printToStdout) System.out.println("I/" + tag + ": " + msg); return 0; }
    public static int e(String tag, String msg) { if (printToStdout) System.err.println("E/" + tag + ": " + msg); return 0; }
    public static int e(String tag, String msg, Throwable tr) { if (printToStdout) { System.err.println("E/" + tag + ": " + msg); tr.printStackTrace(); } return 0; }

    public static boolean isLoggable(String tag, int level) {
        return isLoggableEnabled;
    }
}
