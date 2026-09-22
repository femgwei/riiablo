package com.riiablo.engine.server.cube;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.riiablo.attributes.Attributes;
import com.riiablo.attributes.Stat;
import com.riiablo.codec.excel.Armor;
import com.riiablo.RiiabloTest;
import com.riiablo.item.Item;
import org.junit.jupiter.api.Test;

class HoradricCubeTest extends RiiabloTest {
  @Test
  void transmuteCarriesForcedEtherealOutputAndAppliesItOnce() {
    HoradricCube cube = new HoradricCube();
    CubeRecipe recipe = new CubeRecipe(900, CubeRecipeType.QUEST);
    recipe.inputs = new CubeRecipe.CubeInput[] {
        new CubeRecipe.CubeInput("src", 1)
    };
    recipe.inputCount = 1;
    recipe.output.itemCode = "out";
    recipe.output.ethereal = true;
    cube.clearRecipes();
    cube.registerRecipe(recipe);

    com.badlogic.gdx.utils.Array<String> input = new com.badlogic.gdx.utils.Array<>();
    input.add("src");
    HoradricCube.TransmuteResult result = cube.transmute(input, 1, 1, 0);
    assertTrue(result.success);
    assertTrue(result.outputEthereal);

    Item item = armorItem();
    assertTrue(HoradricCube.applyOutputTraits(item, recipe));
    assertFalse(HoradricCube.applyOutputTraits(item, recipe));
    assertEquals(150, item.attrs.base().get(Stat.armorclass).asInt());
    assertEquals(21, item.attrs.base().get(Stat.maxdurability).asInt());
  }

  private static Item armorItem() {
    Armor.Entry base = new Armor.Entry();
    base.invwidth = 2;
    base.invheight = 2;
    base.durability = 40;
    Item item = new Item();
    item.reset();
    item.base = base;
    item.attrs = Attributes.obtainStandard();
    item.attrs.base().clear();
    item.attrs.base().put(Stat.armorclass, 100);
    item.attrs.base().put(Stat.maxdurability, 40);
    item.attrs.base().put(Stat.durability, 30);
    return item;
  }
}
