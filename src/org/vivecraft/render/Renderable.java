package org.vivecraft.render;

import org.vivecraft.gameplay.trackers.Tracker;

public interface Renderable {
    void render(float partialTicks, RenderStage stage);

    enum RenderStage{PRE_RENDER, RENDER, PRE_WORLD, POST_WORLD, DEBUG, POST_RENDER }
}
