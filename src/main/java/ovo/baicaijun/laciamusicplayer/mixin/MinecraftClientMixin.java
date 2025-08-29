package ovo.baicaijun.laciamusicplayer.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;

/**
 * @author BaicaijunOvO
 * @date 2025/08/27 22:46
 **/
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "stop", at= @At("HEAD"))
    private void stop(CallbackInfo ci) {
        LaciamusicplayerClient.stop();
    }
}
