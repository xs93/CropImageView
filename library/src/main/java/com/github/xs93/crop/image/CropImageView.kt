package com.github.xs93.crop.image

import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ScaleGestureDetector.SimpleOnScaleGestureListener
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.GestureDetectorCompat
import kotlin.math.roundToInt


/**
 * 裁剪图片View ，根据比例裁剪对应的图形
 *
 * @author XuShuai
 * @version v1.0
 * @date 2023/4/27 9:24
 * @email 466911254@qq.com
 */
class CropImageView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr), OnLayoutChangeListener {

    companion object {
        private const val DEFAULT_MAX_SCALE = 3
        private const val DEFAULT_ANIM_DURATION = 200L
        private const val DEFAULT_LINE_ANIM_DURATION = 200L
        private const val DEFAULT_CROP_MASK_COLOR = 0x99000000.toInt()
    }

    private var mAnimDuration = DEFAULT_ANIM_DURATION

    private var mScaleEnable: Boolean = true
    private var mMaxScale = DEFAULT_MAX_SCALE

    private val mBaseMatrix: Matrix = Matrix()
    private val mSuppMatrix: Matrix = Matrix()
    private val mDrawMatrix: Matrix = Matrix()

    private val mMatrixValues = FloatArray(9)

    private var mLastScaleFocusX: Float = 0f
    private var mLastScaleFocusY: Float = 0f

    private var mUpAnim: ValueAnimator? = null

    private var mCropRatioWidth: Float = 3f
    private var mCropRatioHeight: Float = 2f
    private var mCropBackground: Int = DEFAULT_CROP_MASK_COLOR
    private var mCropRectPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val mXfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    private var mCropRectF = RectF()

    private var mShowCropLine = true
    private var mCropLinesWidth = 4f

    private var mCropSubLinesPath = Path()
    private var mCropLinesPathPaint = Paint(Paint.DITHER_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var mShowCropLinesPathAnim: ValueAnimator? = null
    private var mHideCropLinesPathAnim: ValueAnimator? = null
    private var mCropLinesAnimDuration = DEFAULT_LINE_ANIM_DURATION

    private val mOnGestureListener = object : SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            mSuppMatrix.postTranslate(-distanceX, -distanceY)
            checkAndDisplayMatrix()
            return true
        }
    }

    private val mOnScaleGestureListener = object : SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {

            if (mScaleEnable) {
                val curScale = getScale(mSuppMatrix)
                var scaleFactor = detector.scaleFactor
                val newScale = curScale * scaleFactor
                if (newScale > mMaxScale) {
                    scaleFactor = mMaxScale / curScale
                }
                mLastScaleFocusX = detector.focusX
                mLastScaleFocusY = detector.focusY
                mSuppMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                checkAndDisplayMatrix()
            }
            return true
        }

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            mLastScaleFocusX = detector.focusX
            mLastScaleFocusY = detector.focusY
            return true
        }
    }

    private val mGestureDetector = GestureDetectorCompat(context, mOnGestureListener)
    private val mScaleGestureDetector = ScaleGestureDetector(context, mOnScaleGestureListener)


    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.CropImageView)
        mShowCropLine = ta.getBoolean(R.styleable.CropImageView_civ_show_crop_line, true)
        mCropRatioWidth = ta.getFloat(R.styleable.CropImageView_civ_crop_ratio_width, 1f)
        mCropRatioHeight = ta.getFloat(R.styleable.CropImageView_civ_crop_ratio_height, 1f)
        mCropLinesWidth = ta.getDimension(R.styleable.CropImageView_civ_crop_line_width, 4f)
        mCropBackground = ta.getColor(R.styleable.CropImageView_civ_crop_mask_color, DEFAULT_CROP_MASK_COLOR)
        ta.recycle()

        scaleType = ScaleType.MATRIX
        mCropRectPaint.xfermode = mXfermode
        mCropLinesPathPaint.apply {
            style = Paint.Style.FILL_AND_STROKE
            color = Color.WHITE
            strokeWidth = mCropLinesWidth
            alpha = 0
        }
    }


    //region 重写方法

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateCropRect()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCropRect()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            mUpAnim?.cancel()
            if (mShowCropLine) {
                showCliPath()
            }
        }
        mGestureDetector.onTouchEvent(event)
        mScaleGestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            scaleAndTranslateToCenter()
            hideClipPath()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawColor(mCropBackground)
        canvas.drawRect(mCropRectF, mCropRectPaint)
        canvas.restoreToCount(layer)
        if (mShowCropLine) {
            canvas.drawPath(mCropSubLinesPath, mCropLinesPathPaint)
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        update()
    }

    override fun setImageBitmap(bm: Bitmap?) {
        super.setImageBitmap(bm)
        update()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        update()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        update()
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val change = super.setFrame(l, t, r, b)
        if (change) {
            update()
        }
        return change
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix()
        }
    }
    //endregion


    private fun updateBaseMatrix() {
        if (drawable == null) {
            return
        }
        val viewWidth = getImageViewWidth()
        val viewHeight = getImageViewHeight()

        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight

        val widthScale = viewWidth.toFloat() / drawableWidth
        val heightScale = viewHeight.toFloat() / drawableHeight
        val scale = widthScale.coerceAtLeast(heightScale)
        mBaseMatrix.reset()
        mBaseMatrix.postScale(scale, scale)
        mBaseMatrix.postTranslate((viewWidth - drawableWidth * scale) / 2f, (viewHeight - drawableHeight * scale) / 2f)
        resetMatrix()
    }

    private fun update() {
        //这里必须判空，该方法调用时,类肯还未初始化完成
        if (mSuppMatrix != null) {
            if (mScaleEnable) {
                updateBaseMatrix()
            } else {
                resetMatrix()
            }
        }
    }

    private fun getDrawMatrix(): Matrix {
        mDrawMatrix.set(mBaseMatrix)
        mDrawMatrix.postConcat(mSuppMatrix)
        return mDrawMatrix
    }

    private fun resetMatrix() {
        mSuppMatrix.reset()
        checkAndDisplayMatrix()
    }

    private fun checkAndDisplayMatrix() {
        imageMatrix = getDrawMatrix()
    }

    private fun getImageViewWidth(): Int {
        return width - paddingLeft - paddingRight
    }

    private fun getImageViewHeight(): Int {
        return height - paddingTop - paddingBottom
    }

    private fun getScale(matrix: Matrix): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[Matrix.MSCALE_Y]
    }

    private fun getValue(matrix: Matrix, valueType: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[valueType]
    }

    private fun updateCropRect() {
        val viewWidth = measuredWidth
        val viewHeight = measuredHeight
        if (viewWidth == 0 || viewHeight == 0 || mCropRatioWidth == 0.0f || mCropRatioHeight == 0f) {
            return
        }
        val rectWidth: Float
        val rectHeight: Float
        if (mCropRatioWidth >= mCropRatioHeight) {
            rectWidth = viewWidth.toFloat()
            rectHeight = viewWidth / mCropRatioWidth * mCropRatioHeight
        } else {
            rectHeight = viewHeight.toFloat()
            rectWidth = viewHeight / mCropRatioHeight * mCropRatioWidth
        }
        val deltaX = (viewWidth - rectWidth) / 2f
        val deltaY = (viewHeight - rectHeight) / 2f
        mCropRectF.set(deltaX, deltaY, deltaX + rectWidth, deltaY + rectHeight)

        mCropSubLinesPath.reset()

        val widthLineLength = rectWidth / 3f
        val heightLineLength = rectHeight / 3f

        mCropSubLinesPath.moveTo(mCropRectF.left + widthLineLength, mCropRectF.top)
        mCropSubLinesPath.lineTo(mCropRectF.left + widthLineLength, mCropRectF.bottom)
        mCropSubLinesPath.moveTo(mCropRectF.left + widthLineLength * 2, mCropRectF.top)
        mCropSubLinesPath.lineTo(mCropRectF.left + widthLineLength * 2, mCropRectF.bottom)

        mCropSubLinesPath.moveTo(mCropRectF.left, mCropRectF.top + heightLineLength)
        mCropSubLinesPath.lineTo(mCropRectF.right, mCropRectF.top + heightLineLength)
        mCropSubLinesPath.moveTo(mCropRectF.left, mCropRectF.top + heightLineLength * 2)
        mCropSubLinesPath.lineTo(mCropRectF.right, mCropRectF.top + heightLineLength * 2)
    }

    private fun scaleAndTranslateToCenter() {
        if (drawable == null) {
            return
        }
        mUpAnim?.cancel()
        val displayRectF = RectF()
        displayRectF.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        getDrawMatrix().mapRect(displayRectF)

        val oldScale = getScale(mSuppMatrix)
        if (oldScale >= 1) {
            val rw = displayRectF.width()
            val rh = displayRectF.height()
            val viewWidth = getImageViewWidth()
            val viewHeight = getImageViewHeight()
            var deltaX = 0f
            var deltaY = 0f
            if (rw <= viewWidth) {
                deltaX = (viewWidth - rw) / 2f - displayRectF.left
            }
            if (rh <= viewHeight) {
                deltaY = (viewHeight - rh) / 2f - displayRectF.top
            }

            if (rw >= viewWidth && displayRectF.left > 0) {
                deltaX = -displayRectF.left
            }
            if (rw >= viewWidth && displayRectF.right < viewWidth) {
                deltaX = viewWidth - displayRectF.right
            }

            if (rh >= viewHeight && displayRectF.top > 0) {
                deltaY = -displayRectF.top
            }

            if (rh >= viewHeight && displayRectF.bottom < viewHeight) {
                deltaY = viewHeight - displayRectF.bottom
            }
            if (deltaX == 0f && deltaY == 0f) {
                return
            }
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = mAnimDuration
                interpolator = LinearInterpolator()
                addUpdateListener(object : AnimatorUpdateListener {
                    private var lastValue: Float = 0f
                    override fun onAnimationUpdate(animation: ValueAnimator) {
                        val value = animation.animatedValue as Float
                        var diff = value - lastValue
                        if (diff < 0f) {
                            diff = 0f
                        }
                        val transX = deltaX * diff
                        val transY = deltaY * diff
                        mSuppMatrix.postTranslate(transX, transY)
                        checkAndDisplayMatrix()
                        lastValue = value
                    }
                })
            }.also {
                mUpAnim = it
            }.start()
        } else {
            val startX = getValue(mSuppMatrix, Matrix.MTRANS_X)
            val startY = getValue(mSuppMatrix, Matrix.MTRANS_Y)
            val deltaScale = 1 - oldScale
            val deltaX = 0 - startX
            val deltaY = 0 - startY
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = mAnimDuration
                interpolator = LinearInterpolator()
                addUpdateListener(object : AnimatorUpdateListener {
                    private var lastValue: Float = 0f
                    override fun onAnimationUpdate(animation: ValueAnimator) {
                        val value = animation.animatedValue as Float
                        val scale = oldScale + value * deltaScale
                        val transX = startX + value * deltaX
                        val transY = startY + value * deltaY
                        mSuppMatrix.reset()
                        mSuppMatrix.postScale(scale, scale)
                        mSuppMatrix.postTranslate(transX, transY)
                        checkAndDisplayMatrix()
                        lastValue = value
                    }
                })
            }.also {
                mUpAnim = it
            }.start()
        }
    }


    private fun showCliPath() {
        mShowCropLinesPathAnim?.cancel()
        mHideCropLinesPathAnim?.cancel()
        val alpha = mCropLinesPathPaint.alpha
        if (alpha == 255) {
            return
        }
        ValueAnimator.ofInt(alpha, 255).apply {
            duration = mCropLinesAnimDuration
            interpolator = LinearInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                mCropLinesPathPaint.alpha = value
                invalidate()
            }
        }.also {
            mShowCropLinesPathAnim = it
        }.start()
    }

    private fun hideClipPath() {
        mShowCropLinesPathAnim?.cancel()
        mHideCropLinesPathAnim?.cancel()
        val alpha = mCropLinesPathPaint.alpha
        if (alpha == 0) {
            return
        }
        ValueAnimator.ofInt(alpha, 0).apply {
            duration = mCropLinesAnimDuration
            interpolator = LinearInterpolator()
            addUpdateListener {
                val value = it.animatedValue as Int
                mCropLinesPathPaint.alpha = value
                invalidate()
            }
        }.also {
            mHideCropLinesPathAnim = it
        }.start()
    }

    fun setCropRatio(width: Float, height: Float) {
        mCropRatioWidth = width
        mCropRatioHeight = height
        updateCropRect()
        invalidate()
    }

    fun setShowCropLine(show: Boolean) {
        mShowCropLine = show
        invalidate()
    }

    fun setCropLineWidth(width: Float) {
        mCropLinesWidth = width
        mCropLinesPathPaint.strokeWidth = width
        invalidate()
    }

    fun setCropMaskColor(color: Int) {
        mCropBackground = color
        invalidate()
    }

    fun getCropBitmap(baseWidth: Int = 0): Bitmap? {
        if (drawable == null) {
            return null
        }
        val width = mCropRectF.width().toInt()
        val height = mCropRectF.height().toInt()
        if (width == 0 || height == 0) {
            return null
        }
        var canvasScale = 1.0f
        var bitmapWidth = width
        var bitmapHeight = height

        if (baseWidth != 0) {
            if (width < height) {
                canvasScale = baseWidth * 1.0f / width
                bitmapWidth = baseWidth
                bitmapHeight = (height * canvasScale).roundToInt()
            } else {
                canvasScale = baseWidth * 1.0f / height
                bitmapWidth = (width * canvasScale).roundToInt()
                bitmapHeight = baseWidth
            }
        }
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(canvasScale, canvasScale)
        canvas.translate(-mCropRectF.left, -mCropRectF.top)
        canvas.save()
        canvas.concat(getDrawMatrix())
        drawable.draw(canvas)
        canvas.restore()
        return bitmap
    }
}