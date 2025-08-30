package ovo.baicaijun.laciamusicplayer.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovo.baicaijun.laciamusicplayer.Laciamusicplayer;

/**
 * @author BaicaijunOvO
 * @date 2025/08/25 16:26
 **/
@Mixin(InGameHud.class)
public class InGameHudMixin {

//    //1.21.1
//    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/LayeredDrawer;render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",shift = At.Shift.AFTER))
//    private void onRender(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
//        Laciamusicplayer.setContext(context);
//    }

    //1.20.4
    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(DrawContext context, float tickDelta, CallbackInfo ci) {
        Laciamusicplayer.setContext(context);
    }
}
