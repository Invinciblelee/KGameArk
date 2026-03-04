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

/**
 * Defines the execution order of an ECS system.
 * Higher values are executed later in the frame.
 */
class SystemPriority(val value: Int) : Comparable<SystemPriority> {

    override fun compareTo(other: SystemPriority): Int = value.compareTo(other.value)

    infix fun after(offset: Int): SystemPriority = SystemPriority(value + offset)

    infix fun before(offset: Int): SystemPriority = SystemPriority(value - offset)

    /**
     * Named companion object following PascalCase conventions.
     * These anchors define the standard stages of the game loop.
     */
    companion object {
        val Min = SystemPriority(0)
        val Default = SystemPriority(5000)
        val Max = SystemPriority(10000)
    }
}