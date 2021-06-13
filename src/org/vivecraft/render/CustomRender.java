package org.vivecraft.render;

import org.lwjgl.opengl.GL11;
import org.vivecraft.utils.Debug;

import java.util.*;

public class CustomRender {
    public static HashMap<Renderable, List<Renderable.RenderStage>> renders = new HashMap<>();

    static {
        register(Debug.getRenderer(), Renderable.RenderStage.DEBUG);
    }

    public static void register(Renderable render, Renderable.RenderStage... stages){
        renders.put(render, Arrays.asList(stages));
    }


    public static void renderStage(float partialTick, Renderable.RenderStage stage){
        for(Map.Entry<Renderable, List<Renderable.RenderStage>> entry : renders.entrySet()){
            if(entry.getValue().contains(stage)){
                //GL11.glPushMatrix();
                entry.getKey().render(partialTick,stage);
                //GL11.glPopMatrix();
            }
        }
    }

}
