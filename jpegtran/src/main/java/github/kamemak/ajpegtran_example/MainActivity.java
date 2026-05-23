package github.kamemak.ajpegtran_example;

public class MainActivity {
    static {
        System.loadLibrary("ajpegtran");
    }
    public native String ajpegtran(int rfd, int wfd, String optionstr);
    public native String ajpegtranhead(int fd, int[] retarray);
}
