package net.vitrix.pvpislifemod.mixin;

import net.minecraft.client.KeyMapping;

import net.vitrix.pvpislifemod.PvpIsLifeModClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reports every key state change with the exact time it happened, which is what makes
 * millisecond precision possible. {@code setDown} is only called from the input path,
 * from focus regain and from screen open/close; the client side handler filters those out.
 */
@Mixin(KeyMapping.class)
public class KeyMappingMixin {
	@Inject(method = "setDown", at = @At("HEAD"))
	private void pvpislifemod$onSetDown(boolean down, CallbackInfo callbackInfo) {
		PvpIsLifeModClient.onKeyMappingChanged((KeyMapping) (Object) this, down);
	}
}
