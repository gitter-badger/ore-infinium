package com.ore.infinium.components

import com.artemis.Component

/**
 * ***************************************************************************
 * Copyright (C) 2014 by Shaun Reich @gmail.com>                    *
 * *
 * This program is free software; you can redistribute it and/or            *
 * modify it under the terms of the GNU General Public License as           *
 * published by the Free Software Foundation; either version 2 of           *
 * the License, or (at your option) any later version.                      *
 * *
 * This program is distributed in the hope that it will be useful,          *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of           *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
 * GNU General Public License for more details.                             *
 * *
 * You should have received a copy of the GNU General Public License        *
 * along with this program.  If not, see //www.gnu.org/licenses/>.    *
 * ***************************************************************************
 */
class ToolComponent : Component() {

    var type = ToolType.Drill
    var material = ToolMaterial.Wood
    var attackRadius = 10.0f

    /**
     * number of ticks that can pass since last attack
     * before another attack is allowed
     */
    var attackTickInterval = 400

    //damage tool does to blocks
    var blockDamage: Float = 0f

    enum class ToolType {
        Drill,
        Axe,
        Bucket
    }

    enum class ToolMaterial {
        Wood,
        Stone,
        Steel,
        Diamond
    }

    /**
     * copy a component (similar to copy constructor)

     * @param toolComponent
     * *         component to copy from, into this instance
     */
    fun copyFrom(toolComponent: ToolComponent) {
        type = toolComponent.type
        material = toolComponent.material
        attackRadius = toolComponent.attackRadius
        blockDamage = toolComponent.blockDamage
    }

    override fun toString(): String {
        val c = javaClass.simpleName
        return """
        $c.type: $type
        $c.material: $material
        $c.attackRadius: $attackRadius
        $c.blockDamage: $blockDamage"""
    }
}
