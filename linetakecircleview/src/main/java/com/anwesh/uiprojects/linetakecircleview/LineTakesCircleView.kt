package com.anwesh.uiprojects.linetakecircleview

/**
 * Created by anweshmishra on 25/06/19.
 */

import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.app.Activity
import android.content.Context

val nodes : Int = 5
val lines : Int = 2
val scGap : Float = 0.05f
val scDiv : Double = 0.51
val strokeFactor : Int = 90
val sizeFactor : Float = 2.9f
val foreColor : Int = Color.parseColor("#01579B")
val backColor : Int = Color.parseColor("#BDBDBD")
val deg : Float = 90f
val rFactor : Float = 3f
val rotParts : Int = 2
val delay : Long = 30
val circleFactor : Float = 0.5f
val startDeg : Float = -90f
val endDeg : Float = 360f
val circleXFactor : Float = 0.4f
val parts : Int = 2

fun Int.inverse() : Float = 1f / this
fun Float.scaleFactor() : Float = Math.floor(this / scDiv).toFloat()
fun Float.maxScale(i : Int, n : Int) : Float = Math.max(0f, this - i * n.inverse())
fun Float.divideScale(i : Int, n : Int) : Float = Math.min(n.inverse(), maxScale(i, n)) * n
fun Float.mirrorValue(a : Int, b : Int) : Float {
    val k : Float = scaleFactor()
    return (1 - k) * a.inverse() + k * b.inverse()
}
fun Float.updateValue(dir : Float, a : Int, b : Int) : Float = mirrorValue(a, b) * dir * scGap

fun Canvas.drawProgressCircle(i : Int, w : Float, scale : Float, size : Float, paint : Paint) {
    paint.style = Paint.Style.STROKE
    val r : Float = circleFactor * size
    save()
    translate(circleXFactor * w * (1 - 2 * i), 0f)
    drawArc(RectF(-r, -r, r, r), startDeg, endDeg * scale.divideScale(i, parts), false, paint)
    restore()
}

fun Canvas.drawProgressCircles(w : Float, scale : Float, size : Float, paint : Paint) {
    for (j in 0..(parts - 1)) {
        drawProgressCircle(j, w, scale, size, paint)
    }
}

fun Canvas.drawLineCircle(sc : Float, size : Float, rot : Float, paint : Paint) {
    paint.style = Paint.Style.FILL
    save()
    rotate(rot)
    drawCircle(size, 0f, size / rFactor, paint)
    save()
    rotate(deg * sc.divideScale(0, rotParts))
    drawLine(0f, 0f, 0f, -size, paint)
    restore()
    restore()
}

fun Canvas.drawLTCNode(i : Int, scale : Float, paint : Paint) {
    val w : Float = width.toFloat()
    val h : Float = height.toFloat()
    val gap : Float = h / (nodes + 1)
    val size : Float = gap / sizeFactor
    var rot : Float = 0f
    val sc1 : Float = scale.divideScale(0, 2)
    val sc2 : Float = scale.divideScale(1, 2)
    paint.color = foreColor
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeWidth = Math.min(w, h) / strokeFactor
    save()
    translate(w / 2, gap * (i + 1))
    drawProgressCircles(w, scale, size, paint)
    rotate(deg * 2 * sc2)
    for (j in 0..(lines - 1)) {
        val sc : Float = sc1.divideScale(j, lines)
        rot += deg * sc.divideScale(1, rotParts)
        drawLineCircle(sc, size, rot, paint)
    }
    restore()
}

class LineTakesCircleView(ctx : Context) : View(ctx) {

    private val paint : Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val renderer : Renderer = Renderer(this)

    override fun onDraw(canvas : Canvas) {
        renderer.render(canvas, paint)
    }

    override fun onTouchEvent(event : MotionEvent) : Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                renderer.handleTap()
            }
        }
        return true
    }

    data class State(var scale : Float = 0f, var prevScale : Float = 0f, var dir : Float = 0f) {

        fun update(cb : (Float) -> Unit) {
            scale += scale.updateValue(dir, lines, 1)
            if (Math.abs(scale - prevScale) > 1) {
                scale = prevScale + dir
                dir = 0f
                prevScale = scale
                cb(prevScale)
            }
        }

        fun startUpdating(cb : () -> Unit) {
            if (dir == 0f) {
                dir = 1f - 2 * prevScale
                cb()
            }
        }
    }

    data class Animator(var view : View, var animated : Boolean = false) {

        fun animate(cb : () -> Unit) {
            if (animated) {
                cb()
                try {
                    Thread.sleep(delay)
                    view.invalidate()
                } catch(ex : Exception) {

                }
            }
        }

        fun start() {
            if (!animated) {
                animated = true
                view.postInvalidate()
            }
        }

        fun stop() {
            if (animated) {
                animated = false
            }
        }
    }

    data class LTCNode(var i : Int, val state : State = State()) {

        private var next : LTCNode? = null
        private var prev : LTCNode? = null

        init {
            addNeighbor()
        }

        fun addNeighbor() {
            if (i < nodes - 1) {
                next = LTCNode(i + 1)
                next?.prev = this
            }
        }

        fun draw(canvas : Canvas, paint : Paint) {
            canvas.drawLTCNode(i, state.scale, paint)
            next?.draw(canvas, paint)
        }

        fun update(cb : (Int, Float) -> Unit) {
            state.update {
                cb(i, it)
            }
        }

        fun startUpdating(cb : () -> Unit) {
            state.startUpdating(cb)
        }

        fun getNext(dir : Int, cb : () -> Unit) : LTCNode {
            var curr : LTCNode? = prev
            if (dir == 1) {
                curr = next
            }
            if (curr != null) {
                return curr
            }
            cb()
            return this
        }
    }

    data class LineTakeCircle(var i : Int) {

        private val root : LTCNode = LTCNode(0)
        private var curr : LTCNode = root
        private var dir : Int = 1

        fun draw(canvas : Canvas, paint : Paint) {
            root.draw(canvas, paint)
        }

        fun update(cb : (Int, Float) -> Unit) {
            curr.update {i, scl ->
                curr = curr.getNext(dir) {
                    dir *= -1
                }
                cb(i, scl)
            }
        }

        fun startUpdating(cb : () -> Unit) {
            curr.startUpdating(cb)
        }
    }

    data class Renderer(var view : LineTakesCircleView) {

        private val animator : Animator = Animator(view)
        private val ltc : LineTakeCircle = LineTakeCircle(0)

        fun render(canvas : Canvas, paint : Paint) {
            canvas.drawColor(backColor)
            ltc.draw(canvas, paint)
            animator.animate {
                ltc.update {i, scl ->
                    animator.stop()
                }
            }
        }

        fun handleTap() {
            ltc.startUpdating {
                animator.start()
            }
        }
    }

    companion object {

        fun create(activity: Activity) : LineTakesCircleView {
            val view : LineTakesCircleView = LineTakesCircleView(activity)
            activity.setContentView(view)
            return view
        }
    }
}
