package io.github.sceneview.animation

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import dev.romainguy.kotlin.math.Quaternion
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Transform
import io.github.sceneview.math.quaternion
import io.github.sceneview.node.Node

/**
 * Factory for Android [android.animation.Animator]s that drive a [Node]'s transform.
 *
 * Each function builds an animator interpolating through the supplied keyframe values; start
 * it like any other Android animator (`.apply { duration = ...; start() }`). The animators
 * update node properties on each frame, so they must be started and run on the main thread.
 */
object NodeAnimator {

    /**
     * Builds an [AnimatorSet] that animates [node]'s position, rotation and scale together
     * through the given [transforms] keyframes.
     */
    fun ofTransform(node: Node, vararg transforms: Transform) = AnimatorSet().apply {
        playTogether(
            ofPosition(node, *transforms.map { it.position }.toTypedArray()),
            ofQuaternion(node, *transforms.map { it.quaternion }.toTypedArray()),
            ofScale(node, *transforms.map { it.scale }.toTypedArray())
        )
    }

    /**
     * Builds an [ObjectAnimator] that interpolates [node]'s [Node.position] through the given
     * [positions] keyframes.
     */
    fun ofPosition(node: Node, vararg positions: Position): ObjectAnimator {
        val target = Position(node.position)
        return ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat("x", *positions.map { it.x }.toFloatArray()),
            PropertyValuesHolder.ofFloat("y", *positions.map { it.y }.toFloatArray()),
            PropertyValuesHolder.ofFloat("z", *positions.map { it.z }.toFloatArray())
        ).apply {
            addUpdateListener { node.position = target }
        }
    }

    /**
     * Builds an [ObjectAnimator] that interpolates [node]'s [Node.quaternion] through the
     * given [quaternions] keyframes. Prefer this over [ofRotation] for smooth rotation that
     * avoids gimbal lock and shortest-path issues.
     */
    fun ofQuaternion(node: Node, vararg quaternions: Quaternion): ObjectAnimator {
        val target = Quaternion(node.quaternion)
        return ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat("x", *quaternions.map { it.x }.toFloatArray()),
            PropertyValuesHolder.ofFloat("y", *quaternions.map { it.y }.toFloatArray()),
            PropertyValuesHolder.ofFloat("z", *quaternions.map { it.z }.toFloatArray()),
            PropertyValuesHolder.ofFloat("w", *quaternions.map { it.w }.toFloatArray())
        ).apply {
            addUpdateListener { node.quaternion = target }
        }
    }

    /**
     * Builds an [ObjectAnimator] that interpolates [node]'s [Node.rotation] (Euler angles in
     * degrees) through the given [rotations] keyframes. For continuous spins use [ofQuaternion]
     * instead.
     */
    fun ofRotation(node: Node, vararg rotations: Rotation): ObjectAnimator {
        val target = Rotation(node.rotation)
        return ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat("x", *rotations.map { it.x }.toFloatArray()),
            PropertyValuesHolder.ofFloat("y", *rotations.map { it.y }.toFloatArray()),
            PropertyValuesHolder.ofFloat("z", *rotations.map { it.z }.toFloatArray())
        ).apply {
            addUpdateListener { node.rotation = target }
        }
    }

    /**
     * Builds an [ObjectAnimator] that interpolates [node]'s [Node.scale] through the given
     * [scales] keyframes.
     */
    fun ofScale(node: Node, vararg scales: Scale): ObjectAnimator {
        val target = Scale(node.scale)
        return ObjectAnimator.ofPropertyValuesHolder(
            target,
            PropertyValuesHolder.ofFloat("x", *scales.map { it.x }.toFloatArray()),
            PropertyValuesHolder.ofFloat("y", *scales.map { it.y }.toFloatArray()),
            PropertyValuesHolder.ofFloat("z", *scales.map { it.z }.toFloatArray())
        ).apply {
            addUpdateListener { node.scale = target }
        }
    }
}
