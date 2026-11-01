package mtrnext.mixin;

import mtr.block.BlockNode;
import mtr.data.RailwayData;
import mtr.item.ItemNodeModifierBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemNodeModifierBase.class, remap = false)
public class ItemNodeModifierBaseDebugMixin {
	@Inject(method = "clickCondition", at = @At("HEAD"), remap = false)
	private void mtrnext$debugClickConditionHead(UseOnContext context, CallbackInfoReturnable<Boolean> cir) {
		try {
			Level world = context.getLevel();
			BlockPos pos = context.getClickedPos();
			BlockState state = world.getBlockState(pos);
			ItemStack stack = context.getItemInHand();
			Player player = context.getPlayer();
			System.out.println("[MTRNEXT-DEBUG] brush.clickCondition HEAD side=" + (world.isClientSide ? "CLIENT" : "SERVER")
					+ " item=" + (stack == null ? "null" : stack.getItem().getClass().getName())
					+ " pos=" + pos
					+ " block=" + (state == null ? "null" : state.getBlock().getClass().getName())
					+ " isBlockNode=" + (state != null && state.getBlock() instanceof BlockNode)
					+ " player=" + (player == null ? "null" : player.getName().getString()));
		} catch (Throwable t) {
			System.out.println("[MTRNEXT-DEBUG] brush.clickCondition HEAD EXCEPTION " + t);
		}
	}

	@Inject(method = "clickCondition", at = @At("RETURN"), remap = false)
	private void mtrnext$debugClickConditionReturn(UseOnContext context, CallbackInfoReturnable<Boolean> cir) {
		try {
			System.out.println("[MTRNEXT-DEBUG] brush.clickCondition RETURN -> " + cir.getReturnValue());
		} catch (Throwable t) {
			// ignore
		}
	}

	@Inject(method = "onStartClick", at = @At("HEAD"), remap = false)
	private void mtrnext$debugOnStartClick(UseOnContext context, CompoundTag compoundTag, CallbackInfo ci) {
		try {
			Level world = context.getLevel();
			BlockPos posStart = context.getClickedPos();
			Player player = context.getPlayer();
			BlockState stateStart = world.getBlockState(posStart);
			System.out.println("[MTRNEXT-DEBUG] brush.onStartClick HEAD side=" + (world.isClientSide ? "CLIENT" : "SERVER")
					+ " posStart=" + posStart
					+ " blockStart=" + (stateStart == null ? "null" : stateStart.getBlock().getClass().getName())
					+ " player=" + (player == null ? "null" : player.getName().getString())
					+ " tagHasTransport=" + (compoundTag == null ? "null" : compoundTag.contains("transport_mode")));
		} catch (Throwable t) {
			System.out.println("[MTRNEXT-DEBUG] brush.onStartClick EXCEPTION " + t);
		}
	}

	@Inject(method = "onStartClick", at = @At("RETURN"), remap = false)
	private void mtrnext$debugOnStartClickReturn(UseOnContext context, CompoundTag compoundTag, CallbackInfo ci) {
		try {
			System.out.println("[MTRNEXT-DEBUG] brush.onStartClick RETURN tag=" + (compoundTag == null ? "null" : compoundTag.toString()));
		} catch (Throwable t) {
			// ignore
		}
	}

	@Inject(method = "onEndClick", at = @At("HEAD"), remap = false)
	private void mtrnext$debugOnEndClickHead(UseOnContext context, BlockPos posEnd, CompoundTag compoundTag, CallbackInfo ci) {
		try {
			Level world = context.getLevel();
			RailwayData railwayData = RailwayData.getInstance(world);
			BlockPos posStart = context.getClickedPos();
			BlockState stateStart = world.getBlockState(posStart);
			BlockState stateEnd = world.getBlockState(posEnd);
			Player player = context.getPlayer();
			ItemStack stack = context.getItemInHand();
			ItemNodeModifierBase self = (ItemNodeModifierBase) (Object) this;
			String transportModeTag = "null";
			if (compoundTag != null) {
				if (compoundTag.contains("transport_mode")) {
					transportModeTag = compoundTag.getString("transport_mode");
				} else {
					transportModeTag = "MISSING";
				}
			}
			String transportModeStart = "null";
			String transportModeEnd = "null";
			if (stateStart.getBlock() instanceof BlockNode) {
				transportModeStart = ((BlockNode) stateStart.getBlock()).transportMode.toString();
			}
			if (stateEnd.getBlock() instanceof BlockNode) {
				transportModeEnd = ((BlockNode) stateEnd.getBlock()).transportMode.toString();
			}
			boolean samePos = posStart.equals(posEnd);
			boolean sameTransport = !transportModeTag.equals("null") && !transportModeTag.equals("MISSING") && transportModeTag.equals(transportModeStart);
			System.out.println("[MTRNEXT-DEBUG] brush.onEndClick HEAD side=" + (world.isClientSide ? "CLIENT" : "SERVER")
					+ " item=" + (stack == null ? "null" : stack.getItem().getClass().getName())
					+ " posStart=" + posStart + " posEnd=" + posEnd + " samePos=" + samePos
					+ " blockStart=" + (stateStart == null ? "null" : stateStart.getBlock().getClass().getName())
					+ " blockEnd=" + (stateEnd == null ? "null" : stateEnd.getBlock().getClass().getName())
					+ " transportModeStart=" + transportModeStart + " transportModeEnd=" + transportModeEnd
					+ " transport_mode(tag)=" + transportModeTag + " tagMatchesStart=" + sameTransport
					+ " railwayData=" + (railwayData == null ? "null" : "ok")
					+ " forNonContinuous=" + self.forNonContinuousMovementNode
					+ " forContinuous=" + self.forContinuousMovementNode
					+ " forAirplane=" + self.forAirplaneNode
					+ " player=" + (player == null ? "null" : player.getName().getString()));
		} catch (Throwable t) {
			System.out.println("[MTRNEXT-DEBUG] brush.onEndClick HEAD EXCEPTION " + t);
			t.printStackTrace();
		}
	}
}
