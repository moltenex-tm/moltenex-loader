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
import net.fabricmc.loader.api.MappingResolver
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MappingResolverTest {
    val mappingResolver: MappingResolver = MoltenexLoader.instance.mappingResolver!!

    val namespaces: Unit
        get() {
            Assertions.assertIterableEquals(
                listOf(
                    "named",
                    "official",
                    "intermediary"
                ), mappingResolver.namespaces
            )
        }


    val currentRuntimeNamespace: Unit
        get() {
            Assertions.assertEquals("named", mappingResolver.currentRuntimeNamespace)
        }

    @Test
    fun mapClassName() {
        Assertions.assertEquals(
            "net.minecraft.client.MinecraftClient",
            mappingResolver.mapClassName("intermediary", "net.minecraft.class_310")
        )
        Assertions.assertEquals(
            "net.minecraft.client.MinecraftClient\$ChatRestriction",
            mappingResolver.mapClassName("intermediary", "net.minecraft.class_310\$class_5859")
        )
        Assertions.assertEquals(
            "net.minecraft.Unknown",
            mappingResolver.mapClassName("intermediary", "net.minecraft.Unknown")
        )
    }

    @Test
    fun unmapClassName() {
        Assertions.assertEquals(
            "net.minecraft.class_6327",
            mappingResolver.unmapClassName("intermediary", "net.minecraft.server.command.DebugPathCommand")
        )
    }

    @Test
    fun mapFieldName() {
        Assertions.assertEquals(
            "world",
            mappingResolver.mapFieldName(
                "intermediary",
                "net.minecraft.class_2586",
                "field_11863",
                "Lnet/minecraft/class_1937;"
            )
        )
    }

    @Test
    fun mapMethodName() {
        Assertions.assertEquals(
            "getWorld",
            mappingResolver.mapMethodName(
                "intermediary",
                "net.minecraft.class_2586",
                "method_10997",
                "()Lnet/minecraft/class_1937;"
            )
        )
    }
}
