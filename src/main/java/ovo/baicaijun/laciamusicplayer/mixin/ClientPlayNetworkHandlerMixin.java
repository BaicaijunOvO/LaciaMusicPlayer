package ovo.baicaijun.laciamusicplayer.mixin;


import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ovo.baicaijun.laciamusicplayer.client.LaciamusicplayerClient;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {

    @Inject(method = "sendChatMessage",at = @At("HEAD"), cancellable = true)
    private void sendChatMessage(String message, CallbackInfo ci) {
        if(message.startsWith("*")) {
            LaciamusicplayerClient.commandManager.run(message);
            ci.cancel();
        }
    }

}
