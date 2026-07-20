package com.starterkit.recipe;

import com.starterkit.StarterKitMod;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 本包所有 RecipeSerializer 的注册中心。
 */
public final class StarterKitRecipes {

    /** 九面包 → 永久饱和喷溅药水。json 里的 type 字段应写作 "starterkit:splash_saturation"。*/
    public static final RecipeSerializer<SplashSaturationRecipe> SPLASH_SATURATION =
        new SpecialRecipeSerializer<>(SplashSaturationRecipe::new);

    private StarterKitRecipes() {}

    public static void register() {
        Registry.register(
            Registries.RECIPE_SERIALIZER,
            new Identifier(StarterKitMod.MOD_ID, "splash_saturation"),
            SPLASH_SATURATION
        );
    }
}
