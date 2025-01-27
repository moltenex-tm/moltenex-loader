/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.fabricmc.minecraft.test.junit

import com.moltenex.loader.api.MoltenexLoader
import net.minecraft.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.block.Blocks
import net.minecraft.block.GrassBlock
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class JunitTest {
    @Test
    fun testItems() {
        val id = Registries.ITEM.getId(Items.DIAMOND)
        Assertions.assertEquals(id.toString(), "minecraft:diamond")

        println(id)
    }

    @Test
    fun testMixin() {
        // MixinGrassBlock sets canGrow to false
        val grassBlock = Blocks.GRASS_BLOCK as GrassBlock
        val canGrow = grassBlock.canGrow(null, null, null, null)
        Assertions.assertFalse(canGrow)
    }

    @Test
    fun testMixinExtras() {
        // MixinGrassBlock sets isFertilizable to true
        val grassBlock = Blocks.GRASS_BLOCK as GrassBlock
        val isFertilizable = grassBlock.isFertilizable(null, BlockPos.ORIGIN, null)
        Assertions.assertTrue(isFertilizable)
    }

    @Test
    fun testAccessLoader() {
        MoltenexLoader.instance.allMods
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup(): Unit {
            SharedConstants.createGameVersion()
            Bootstrap.initialize()
        }
    }
}
