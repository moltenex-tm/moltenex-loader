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
package net.fabricmc.minecraft.test

import com.moltenex.loader.api.MoltenexLoader
import net.fabricmc.api.ModInitializer
import net.fabricmc.loader.api.MappingResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class TestEntrypoint : ModInitializer {
    override fun onInitialize() {
        LOGGER.info("Hello Fabric world!")

        val mappingResolver: MappingResolver = MoltenexLoader.instance.mappingResolver!!
        LOGGER.info(mappingResolver.mapClassName("intermediary", "net.minecraft.class_310"))
    }

    companion object {
        val LOGGER: Logger = LoggerFactory.getLogger("minecraft-test")
    }
}
