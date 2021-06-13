package org.vivecraft.physicalinventory;

import java.util.ArrayList;

import net.minecraft.client.renderer.model.ModelBakery;
import net.minecraft.util.ResourceLocation;

/**
 * Legacy Workaround to get Minecraft to Render our Custom Models as it's own
 * */
public class ExtendedModelManager {
//	public ExtendedModelManager(AtlasTexture p_i1132_1_, BlockColors p_i1132_2_) {
//		super(p_i396_1_, p_i396_2_, p_i396_3_);
//		//super(p_i1132_1_, p_i1132_2_);
//		// TODO Auto-generated constructor stub
//	}

	ArrayList<String> models=new ArrayList<>();

//	public ExtendedModelManager(AtlasTexture textures) {
//		super(textures);
//	}

	//i have no idea what this class wants or does.
//	@Override
//	public void onResourceManagerReload(IResourceManager resourceManager) {
//		
//		
//		ModelBakery modelbakery = new ModelBakery(resourceManager, (AtlasTexture) MCReflection.ModelManager_texmap.get(this), Minecraft.getInstance().getProfiler());
//		putModels(modelbakery);
//
//		
////		
////		Map<ModelResourceLocation, IBakedModel> registry=modelbakery.mapUnbakedModels();
////		
////		MCReflection.ModelManager_modelRegistry.set(this,registry);
////
////		MCReflection.ModelManager_defaultModel.set(this,registry.get(ModelBakery.MODEL_MISSING));
////		
////		getBlockModelShapes().reloadModels();
//	}

	private void putModels(ModelBakery bakery){
		for (String entry : models){
			ResourceLocation resourcelocation = new ResourceLocation(entry);
			
			//Set<ResourceLocation> queue= (Set<ResourceLocation>)
			//	new MCReflection.ReflectionField(ModelBlock.class,"field_209609_E").get(bakery);
			
			//queue.add(resourcelocation);
			
			/*//Method mgetDef=MCReflection.getDeclaredMethod(ModelBakery.class,"getModelBlockDefinition","a","func_177586_a",ResourceLocation.class);
			MCReflection.ReflectionMethod mgetDef=new MCReflection.ReflectionMethod(ModelBakery.class,"func_177586_a",ResourceLocation.class);
			ModelBlockDefinition modelblockdefinition =(ModelBlockDefinition) mgetDef.invoke(bakery,resourcelocation);
			
			
			//Field variants=MCReflection.getDeclaredField(ModelBlockDefinition.class,"mapVariants","b","mapVariants");
			MCReflection.ReflectionField variants=new MCReflection.ReflectionField(ModelManager.class,"mapVariants");
			Map<String,VariantList> map=(Map<String,VariantList>) variants.get(modelblockdefinition);

			for(String var: map.keySet()) {
				//Method mRegister=MCReflection.getDeclaredMethod(ModelBakery.class,"registerVariant","a","func_177569_a", ModelBlockDefinition.class,ModelResourceLocation.class);
				MCReflection.ReflectionMethod mRegister=new MCReflection.ReflectionMethod(ModelBakery.class,"func_177569_a", ModelBlockDefinition.class,ModelResourceLocation.class);
				mRegister.invoke(bakery, modelblockdefinition, new ModelResourceLocation(resourcelocation, var));
			}*/
			
		}
	}

	public void registerModel(String baseId){
		models.add(baseId);
	}

}
