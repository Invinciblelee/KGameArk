/*
 * Created by Quillraven
 * Modified by [Lee] for KGameArk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.kgame.ecs

import kotlinx.atomicfu.atomic

interface Identifiable {

    val id: Int

    /**
     * Using an internal companion object to restrict who can trigger ID generation.
     * Since the generator is private to this file or module, external code cannot
     * manually manipulate the ID sequence.
     */
    companion object {
        // Internal generator invisible to the outside world
        private val generator = atomic(0)

        /** Only used by components during initialization. */
        internal fun nextId(): Int = generator.getAndIncrement()
    }

}