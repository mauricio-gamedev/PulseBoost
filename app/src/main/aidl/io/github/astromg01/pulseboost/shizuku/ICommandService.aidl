package io.github.astromg01.pulseboost.shizuku;

interface ICommandService {
    String exec(String command) = 1;
    void destroy() = 16777114;
}
