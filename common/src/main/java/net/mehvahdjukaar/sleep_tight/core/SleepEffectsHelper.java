package net.mehvahdjukaar.sleep_tight.core;

import net.mehvahdjukaar.sleep_tight.common.HammockBlockEntity;
import net.mehvahdjukaar.sleep_tight.common.IVanillaBed;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

import static net.mehvahdjukaar.sleep_tight.configs.CommonConfigs.*;

public class SleepEffectsHelper {

    public static void applyEffectsOnWakeUp(PlayerSleepData playerCap, ServerPlayer player,
                                            long dayTimeDelta, BlockEntity blockEntity) {
        if (blockEntity instanceof IVanillaBed bed) {
            applyVanillaBedBonuses(player, dayTimeDelta, bed.getBedData());
        }
        if (player.gameMode.isSurvival()) {
            applySleepPenalties(player, dayTimeDelta, blockEntity);
            paySleepRequirements(player, blockEntity);
        }
    }

    private static void applySleepPenalties(ServerPlayer player, long dayTimeDelta, BlockEntity block) {
        if (block == null && !PENALTIES_NIGHT_BAG.get()) return; //only hammocks have null tile of our bocks
        if (block instanceof HammockBlockEntity && !PENALTIES_HAMMOCK.get()) return;
        if (!PENALTIES_BED.get()) return;

        double hunger = CONSUMED_HUNGER.get();
        if (hunger == 0) return;
        HungerMode mode = CONSUME_HUNGER_MODE.get();
        if (mode == HungerMode.DIFFICULTY_BASED || mode == HungerMode.TIME_DIFFICULTY_BASED) {
            int diff = player.getLevel().getDifficulty().getId();
            if (diff == 0) return;
            else hunger *= 1 + ((diff - 1) * 0.25);
        }
        if (mode == HungerMode.TIME_BASED || mode == HungerMode.TIME_DIFFICULTY_BASED) {
            hunger = (hunger / 11000) * dayTimeDelta;
        }

        int level = player.getFoodData().getFoodLevel();
        level = (int) Mth.clamp(level - hunger, 0, 20);
        player.getFoodData().setFoodLevel(level);
    }

    private static void applyVanillaBedBonuses(ServerPlayer player, long dayTimeDelta, BedData data) {

        BedStatus status = BED_BENEFITS.get();
        if (status == BedStatus.NONE) return;
        if (status == BedStatus.HOME_BED && !data.isHomeBedFor(player)) return;

        EffectIntensity healing = HEALING.get();
        if (healing != EffectIntensity.NONE) {
            float maxHealth = player.getMaxHealth();
            float heal = maxHealth;
            if (healing == EffectIntensity.TIME_BASED) {
                heal = (maxHealth / 12000f) * dayTimeDelta;
            }
            player.heal(heal);
        }

        EffectIntensity potionClearing = EFFECT_CLEARING.get();
        if (potionClearing != EffectIntensity.NONE) {
            var type = EFFECT_CLEARING_TYPE.get();
            boolean timeBased = potionClearing == EffectIntensity.TIME_BASED;
            List<MobEffectInstance> toEditOrRemove = new ArrayList<>();
            for (var e : player.getActiveEffects()) {
                if (switch (type) {
                    case ALL -> true;
                    case HARMFUL -> e.getEffect().getCategory() == MobEffectCategory.HARMFUL;
                    case BENEFICIAL -> e.getEffect().getCategory() != MobEffectCategory.HARMFUL;
                }) {
                    toEditOrRemove.add(e);
                }
            }
            for (var e : toEditOrRemove) {
                player.removeEffect(e.getEffect());
                if (timeBased) {
                    long remainingTime = e.getDuration() - dayTimeDelta;
                    if (remainingTime > 0) {
                        CompoundTag tag = new CompoundTag();
                        e.save(tag);
                        tag.putInt("Duration", (int) remainingTime);
                        MobEffectInstance load = MobEffectInstance.load(tag);
                        if (load != null) player.addEffect(load);
                    }
                }
            }
        }
    }

    private static void paySleepRequirements(ServerPlayer serverPlayer, BlockEntity tile) {
        if (tile == null && !REQUIREMENT_NIGHT_BAG.get()) return; //only hammocks have null tile of our bocks
        if (tile instanceof HammockBlockEntity && !REQUIREMENT_HAMMOCK.get()) return;
        if (!REQUIREMENT_BED.get()) return;

        serverPlayer.giveExperiencePoints(-XP_COST.get());
    }

    //true if can sleep
    public static boolean checkExtraRequirements(Player player, @Nullable BlockPos bedPos) {
        if (bedPos != null && !player.getAbilities().instabuild) {
            BlockEntity tile = player.getLevel().getBlockEntity(bedPos);
            if (tile == null && !REQUIREMENT_NIGHT_BAG.get()) return true; //only hammocks have null tile of our bocks
            if (tile instanceof HammockBlockEntity && !REQUIREMENT_HAMMOCK.get()) return true;
            if (!REQUIREMENT_BED.get()) return true;

            int xp = XP_COST.get();
            if (xp != 0 && player.totalExperience < xp){
                if(player.level.isClientSide){
                    player.displayClientMessage(Component.translatable("message.sleep_tight.xp"), true);
                }
                return false;
            }
            if (NEED_FULL_HUNGER.get() && player.getFoodData().needsFood()){
                if(player.level.isClientSide){
                    player.displayClientMessage(Component.translatable("message.sleep_tight.hunger"), true);
                }
                return false;
            }
        }
        return true;
    }

}