package hellfirepvp.astralsorcery.common.crafting.altar.recipes;

import hellfirepvp.astralsorcery.common.block.BlockMarble;
import hellfirepvp.astralsorcery.common.crafting.IAccessibleRecipe;
import hellfirepvp.astralsorcery.common.crafting.helper.ShapeMap;
import hellfirepvp.astralsorcery.common.crafting.helper.ShapedRecipe;
import hellfirepvp.astralsorcery.common.crafting.helper.ShapedRecipeSlot;
import hellfirepvp.astralsorcery.common.item.ItemCraftingComponent;
import hellfirepvp.astralsorcery.common.item.crystal.CrystalProperties;
import hellfirepvp.astralsorcery.common.lib.BlocksAS;
import hellfirepvp.astralsorcery.common.lib.ItemsAS;
import hellfirepvp.astralsorcery.common.tile.TileAltar;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

/**
 * This class is part of the Astral Sorcery Mod
 * The complete source code for this mod can be found on github.
 * Class: PrismLensRecipe
 * Created by HellFirePvP
 * Date: 02.11.2016 / 21:07
 */
public class PrismLensRecipe extends ConstellationRecipe {

    public PrismLensRecipe(boolean celestial) {
        super(new ShapedRecipe(BlocksAS.lensPrism)
                .addPart(Blocks.GLASS_PANE,
                        ShapedRecipeSlot.UPPER_LEFT,
                        ShapedRecipeSlot.LEFT,
                        ShapedRecipeSlot.LOWER_LEFT,
                        ShapedRecipeSlot.UPPER_RIGHT,
                        ShapedRecipeSlot.RIGHT,
                        ShapedRecipeSlot.LOWER_RIGHT)
                .addPart(new ItemStack(ItemsAS.craftingComponent, 1, ItemCraftingComponent.MetaType.STARDUST.getItemMeta()),
                        ShapedRecipeSlot.UPPER_CENTER)
                .addPart(new ItemStack(ItemsAS.craftingComponent, 1, ItemCraftingComponent.MetaType.AQUAMARINE.getItemMeta()),
                        ShapedRecipeSlot.LOWER_CENTER)
                .addPart((celestial ? ItemsAS.celestialCrystal : ItemsAS.rockCrystal),
                        ShapedRecipeSlot.CENTER));

        ItemStack rMarble = new ItemStack(BlocksAS.blockMarble, 1, BlockMarble.MarbleBlockType.RUNED.ordinal());
        setAttItem(rMarble,
                AltarSlot.LOWER_LEFT,
                AltarSlot.LOWER_RIGHT);
        setCstItem(rMarble,
                AltarAdditionalSlot.DOWN_DOWN_LEFT,
                AltarAdditionalSlot.DOWN_DOWN_RIGHT);
        setCstItem(Blocks.LOG,
                AltarAdditionalSlot.DOWN_LEFT_LEFT,
                AltarAdditionalSlot.UP_LEFT_LEFT,
                AltarAdditionalSlot.DOWN_RIGHT_RIGHT,
                AltarAdditionalSlot.UP_RIGHT_RIGHT);
    }

    @Nullable
    @Override
    public ItemStack getOutput(ShapeMap centralGridMap, TileAltar altar) {
        ItemStack lens = super.getOutput(centralGridMap, altar);
        CrystalProperties crystalProp = CrystalProperties.getCrystalProperties(centralGridMap.get(ShapedRecipeSlot.CENTER));
        CrystalProperties.applyCrystalProperties(lens, crystalProp);
        return lens;
    }

}