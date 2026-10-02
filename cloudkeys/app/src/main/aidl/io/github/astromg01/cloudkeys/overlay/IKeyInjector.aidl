package io.github.astromg01.cloudkeys.overlay;

interface IKeyInjector {
    void sendKey(int keyCode);
    void sendTap(int x, int y);
    void destroy();
}
