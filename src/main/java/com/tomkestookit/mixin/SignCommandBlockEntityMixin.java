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
 * 给告示牌 BlockEntity 加三个自定义字符串字段：单条命令、任务组 id、任务组名。
 */
@Mixin(SignBlockEntity.class)
public abstract class SignCommandBlockEntityMixin implements SignCommandBlockEntityAccess {

    @Unique
    private String tomkestookit$command;

    @Unique
    private String tomkestookit$taskGroup;

    @Unique
    private String tomkestookit$taskGroupName;

    @Override
    public String tomkestookit$getCommand() {
        return tomkestookit$command;
    }

    @Override
    public void tomkestookit$setCommand(String command) {
        this.tomkestookit$command = command;
    }

    @Override
    public String tomkestookit$getTaskGroup() {
        return tomkestookit$taskGroup;
    }

    @Override
    public void tomkestookit$setTaskGroup(String taskGroup) {
        this.tomkestookit$taskGroup = taskGroup;
    }

    @Override
    public String tomkestookit$getTaskGroupName() {
        return tomkestookit$taskGroupName;
    }

    @Override
    public void tomkestookit$setTaskGroupName(String taskGroupName) {
        this.tomkestookit$taskGroupName = taskGroupName;
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void tomkestookit$writeCommand(NbtCompound nbt, CallbackInfo ci) {
        if (tomkestookit$command != null && !tomkestookit$command.isEmpty()) {
            nbt.putString(SignCommandHandler.NBT_KEY, tomkestookit$command);
        }
        if (tomkestookit$taskGroup != null && !tomkestookit$taskGroup.isEmpty()) {
            nbt.putString(SignCommandHandler.TASK_GROUP_NBT_KEY, tomkestookit$taskGroup);
        }
        if (tomkestookit$taskGroupName != null && !tomkestookit$taskGroupName.isEmpty()) {
            nbt.putString(SignCommandHandler.TASK_GROUP_NAME_NBT_KEY, tomkestookit$taskGroupName);
        }
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void tomkestookit$readCommand(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains(SignCommandHandler.NBT_KEY)) {
            tomkestookit$command = nbt.getString(SignCommandHandler.NBT_KEY);
        } else {
            tomkestookit$command = null;
        }
        if (nbt.contains(SignCommandHandler.TASK_GROUP_NBT_KEY)) {
            tomkestookit$taskGroup = nbt.getString(SignCommandHandler.TASK_GROUP_NBT_KEY);
        } else {
            tomkestookit$taskGroup = null;
        }
        if (nbt.contains(SignCommandHandler.TASK_GROUP_NAME_NBT_KEY)) {
            tomkestookit$taskGroupName = nbt.getString(SignCommandHandler.TASK_GROUP_NAME_NBT_KEY);
        } else {
            tomkestookit$taskGroupName = null;
        }
    }
}
