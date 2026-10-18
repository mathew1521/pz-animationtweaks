package com.mathew1521.animationtweaks;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import me.zed_0xff.zombie_buddy.Patch;
import zombie.core.skinnedmodel.advancedanimation.AnimLayer;
import zombie.core.skinnedmodel.advancedanimation.LiveAnimNode;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.animation.AnimationTrack;

@Patch(className = "zombie.core.skinnedmodel.advancedanimation.AnimLayer", methodName = "Update", strictMatch = true)
public final class BlendFieldEasingPatch {
    private static final float BLEND_TIME_SECONDS = 0.15F;
    private static final ThreadLocal<Deque<UpdateSnapshot>> SNAPSHOTS =
        ThreadLocal.withInitial(ArrayDeque::new);

    private BlendFieldEasingPatch() {
    }

    @Patch.OnEnter
    public static void beforeUpdate(@Patch.This AnimLayer layer, @Patch.Argument(0) float deltaT) {
        captureBeforeUpdate(layer);
    }

    public static void captureBeforeUpdate(AnimLayer layer) {
        Map<AnimationTrack, Float> weights = new IdentityHashMap<>();
        for (LiveAnimNode node : layer.getLiveAnimNodes()) {
            if (node.isBlendField) {
                for (int i = 0; i < node.getMainAnimationTracksCount(); i++) {
                    AnimationTrack track = node.getMainAnimationTrackAt(i);
                    weights.put(track, node.isNew() ? 0.0F : track.getBlendFieldWeight());
                }
            }
        }

        SNAPSHOTS.get().push(new UpdateSnapshot(layer, weights));
    }

    @Patch.OnExit
    public static void afterUpdate(@Patch.This AnimLayer layer) {
        smoothAfterUpdate(layer);
    }

    public static void smoothAfterUpdate(AnimLayer layer) {
        Deque<UpdateSnapshot> snapshots = SNAPSHOTS.get();
        if (snapshots.isEmpty()) {
            return;
        }

        UpdateSnapshot snapshot = snapshots.pop();
        if (snapshots.isEmpty()) {
            SNAPSHOTS.remove();
        }

        if (snapshot.layer != layer) {
            return;
        }

        for (LiveAnimNode node : layer.getLiveAnimNodes()) {
            if (!node.isBlendField) {
                continue;
            }

            for (int i = 0; i < node.getMainAnimationTracksCount(); i++) {
                AnimationTrack track = node.getMainAnimationTrackAt(i);
                float targetWeight = track.getBlendFieldWeight();
                float currentWeight = snapshot.previousWeights.getOrDefault(track, 0.0F);
                float smoothedWeight = AnimationPlayer.lerpBlendWeight(currentWeight, targetWeight, BLEND_TIME_SECONDS);
                if (smoothedWeight < 1.0E-4F) {
                    smoothedWeight = 0.0F;
                }

                track.setBlendFieldWeight(Math.max(0.0F, Math.min(1.0F, smoothedWeight)));
            }
        }
    }

    private static final class UpdateSnapshot {
        private final AnimLayer layer;
        private final Map<AnimationTrack, Float> previousWeights;

        private UpdateSnapshot(AnimLayer layer, Map<AnimationTrack, Float> previousWeights) {
            this.layer = layer;
            this.previousWeights = previousWeights;
        }
    }

}
