package com.tomkestookit.mixin;

import com.tomkestookit.SignCommandHandler;
import com.tomkestookit.SignCommandBlockEntityAccess;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 给告示牌 BlockEntity 加一个自定义字符串字段 tomkestookit$command，
 * 持久化到 NBT，供 SignCommandHandler 使用。通过实现
 * SignCommandBlockEntityAccess 接口暴露 setter/getter。
 */
@Mixin(SignBlockEntity.class)
public abstract class SignCommandBlockEntityMixin implements SignCommandBlockEntityAccess {

    @Unique
    private String tomkestookit$command;

    @Override
    public String tomkestookit$getCommand() {
        return tomkestookit$command;
    }

    @Override
    public void tomkestookit$setCommand(String command) {
        this.tomkestookit$command = command;
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void tomkestookit$writeCommand(NbtCompound nbt, CallbackInfo ci) {
        if (tomkestookit$command != null && !tomkestookit$command.isEmpty()) {
            nbt.putString(SignCommandHandler.NBT_KEY, tomkestookit$command);
        }
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void tomkestookit$readCommand(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(SignCommandHandler.NBT_KEY)) {
            tomkestookit$command = nbt.getString(SignCommandHandler.NBT_KEY);
        } else {
            tomkestookit$command = null;
        }
    }
}
