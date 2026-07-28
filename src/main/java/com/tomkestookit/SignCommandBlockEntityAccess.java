package com.tomkestookit;

/**
 * SignBlockEntity 的 mixin 实现该接口，供 SignCommandHandler 读写自定义命令字段。
 */
public interface SignCommandBlockEntityAccess {
    String tomkestookit$getCommand();
    void tomkestookit$setCommand(String command);
}
