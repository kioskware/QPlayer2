package com.fivesoft.qplayer.common.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Resolver<D, R> {

    private final Set<Creator<D, R>> creators = new HashSet<>();

    /**
     * Registers the creator in the resolver.<br>
     * @param creator The creator to be registered.
     */

    public Resolver<D, R> register(@NonNull Creator<D, R> creator) {
        synchronized (creators) {
            creators.add(Objects.requireNonNull(creator));
        }
        return this;
    }

    /**
     * Unregisters the creator from the resolver.<br>
     * @param candidate The creator to be unregistered.
     */

    public Resolver<D, R> unregister(@NonNull Creator<D, R> candidate) {
        synchronized (creators) {
            creators.remove(Objects.requireNonNull(candidate));
        }
        return this;
    }

    /**
     * Resolves the descriptor to the result object.<br>
     * @param descriptor The descriptor of the object to be created.
     * @return The result object. May be null if no creator can create such object.
     */

    @Nullable
    public R resolve(@NonNull D descriptor) {
        Objects.requireNonNull(descriptor, "Descriptor cannot be null");
        synchronized (creators) {
            int maxPriority = Integer.MIN_VALUE;
            Creator<D, R> bestCreator = null;
            int creatorPriority;
            for (Creator<D, R> creator : creators) {
                creatorPriority = creator.accept(descriptor);
                if (creatorPriority > 0 && creatorPriority > maxPriority) {
                    maxPriority = creatorPriority;
                    bestCreator = creator;
                }
            }
            if (bestCreator != null) {
                return bestCreator.create(descriptor);
            }
            return null;
        }
    }

}
