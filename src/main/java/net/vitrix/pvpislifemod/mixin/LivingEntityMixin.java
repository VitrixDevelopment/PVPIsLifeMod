package net.vitrix.pvpislifemod.mixin;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import net.vitrix.pvpislifemod.PvpIsLifeModClient;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code handleDamageEvent} is called on the client when a damage packet arrives, for every
 * entity that takes a hit - including the local player and every entity you hit. That makes it
 * the exact client side moment a hit "lands" for timing purposes.
 */
@Mixin(LivingEntity.class)
public class LivingEntityMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void pvpislifemod$onHandleDamageEvent(DamageSource source, CallbackInfo callbackInfo) {
		PvpIsLifeModClient.onLivingDamage((LivingEntity) (Object) this, source);
	}
}
