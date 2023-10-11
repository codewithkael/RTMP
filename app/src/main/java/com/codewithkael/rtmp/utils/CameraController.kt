import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import com.codewithkael.rtmp.utils.CameraInfoModel
import com.codewithkael.rtmp.utils.ExposureMode
import com.codewithkael.rtmp.utils.fromPercent
import com.haishinkit.view.HkSurfaceView
import kotlin.math.max

/**
 * A controller class for managing various camera functionalities and parameters.
 *
 * @param cameraId The ID of the camera device.
 * @param cameraManager The CameraManager instance.
 * @param captureSession The active CameraCaptureSession.
 * @param captureBuilder The CaptureRequest.Builder.
 */
class CameraController(
    private val cameraId: String,
    private val cameraManager: CameraManager,
    private val captureSession: CameraCaptureSession,
    private val captureBuilder: CaptureRequest.Builder,
    private val textureView: HkSurfaceView
) {
    private val TAG = "CameraController"

    /**
     * Get the characteristics of the camera device.
     *
     * @return CameraCharacteristics for the specified camera ID.
     */
    // Function to get camera characteristics
    private fun getCameraCharacteristics(): CameraCharacteristics {
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    fun updateCameraInfo(info: CameraInfoModel, exposureUpdated: Boolean) {
        Log.d(TAG, "updateCameraInfo: $exposureUpdated")
//        textureView.rotation = 270f
        try {
            if (!exposureUpdated) {
                setExposureTime(info.shutterSpeed)
                getIsoRange()?.let { range ->
                    Log.d(TAG, "updateCameraInfo: isoRange $range")
                    val value = info.iso.fromPercent(range)
                    Log.d(TAG, "updateCameraInfo: isoValue $value")

                    setIso(value)
                }
            } else {
                setExposureCompensation(info.exposureCompensation)
            }

            setZoom(info.zoomLevel.toFloat())
            setCustomWhiteBalance(info.red, info.green, info.blue)
            if (info.flashLight) turnOnFlash() else turnOffFlash()
            //focus mode
            setCustomFocusPercent(info.focusPercent * 100)
//            setOrientation(info.orientation)


//            setCameraOrientation(0.toString(),cameraManager,info.orientation)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setOrientation(orientation: Int) {
//        configureTransform(320,480,270)
        captureBuilder.addTarget(textureView.holder.surface)
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    @SuppressLint("NewApi")
    private fun configureTransform(viewWidth: Int, viewHeight: Int, rotation:Int) {

        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val streamConfigurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSize = streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)[0] // Choose an appropriate size

        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect = RectF(0f, 0f, previewSize!!.height.toFloat(), previewSize.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                viewHeight.toFloat() / previewSize.height,
                viewWidth.toFloat() / previewSize.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate((90 * (rotation - 2)).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180f, centerX, centerY)
        }

        textureView.transformMatrixToGlobal(matrix)
    }


    fun setCameraOrientation(cameraId: String, cameraManager: CameraManager, degrees: Int) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

        // Find the output size for the preview
        val outputSizes = configMap?.getOutputSizes(HkSurfaceView::class.java) ?: emptyArray()
        val previewSize = chooseOptimalSize(
            outputSizes, // List of available preview sizes
            1920, 1080 // Desired width and height
        )

        // Configure the texture view size based on orientation
        val orientation = (degrees + getDeviceOrientation()) % 360
//        if (orientation == 90 || orientation == 270) {
//            // Swap width and height if in landscape
//            textureView.setLayoutParams(ViewGroup.LayoutParams(context))
//            textureView.setAspectRatio(previewSize.height, previewSize.width)
//        } else {
//            textureView.setAspectRatio(previewSize.width, previewSize.height)
//        }

        // Apply the rotation to the texture view
        textureView.rotation = degrees.toFloat()

        // Set the orientation in the capture request
        captureBuilder.set(
            CaptureRequest.JPEG_ORIENTATION, getJpegOrientation(characteristics, orientation)
        )
    }

    // Function to choose an optimal size based on desired dimensions
    private fun chooseOptimalSize(choices: Array<Size>, width: Int, height: Int): Size {
        val minSize = choices.firstOrNull {
            it.width >= width && it.height >= height
        }
        return minSize ?: choices.last()
    }

    // Function to get the device orientation (in degrees)
    private fun getDeviceOrientation(): Int {
        val displayRotation =
            (textureView.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

        return when (displayRotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    // Function to get the JPEG orientation based on device orientation
    private fun getJpegOrientation(
        characteristics: CameraCharacteristics, deviceOrientation: Int
    ): Int {
        if (deviceOrientation == android.view.OrientationEventListener.ORIENTATION_UNKNOWN) return 0
        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)

        // Round device orientation to a multiple of 90
        val roundedOrientation = (deviceOrientation + 45) / 90 * 90

        // Reverse device orientation for front-facing cameras
        val facingFront =
            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        return if (facingFront) {
            (sensorOrientation!! + roundedOrientation) % 360
        } else {  // Back-facing
            (sensorOrientation!! - roundedOrientation + 360) % 360
        }
    }


    /**
     * Set the zoom level of the camera.
     *
     * @param zoomLevel The desired zoom level (should be between 1.0 and maxZoom).
     */
    // Function to set zoom level (zoomLevel should be between 1.0 and maxZoom)
    private fun setZoom(zoomLevel: Float) {
        val characteristics = getCameraCharacteristics()
        val maxZoom = characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
        if (maxZoom != null) {
            val zoomRect = calculateZoomRect(characteristics, zoomLevel)
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        }
    }

    /**
     * Perform custom focus at a specific area on the camera preview.
     *
     * @param x The x-coordinate of the top-left corner of the focus area.
     * @param y The y-coordinate of the top-left corner of the focus area.
     * @param width The width of the focus area.
     * @param height The height of the focus area.
     */
    // Function to calculate zoom Rect
    private fun calculateZoomRect(characteristics: CameraCharacteristics, zoomLevel: Float): Rect {
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val centerX = sensorSize?.width()?.div(2) ?: 0
        val centerY = sensorSize?.height()?.div(2) ?: 0

        val deltaX = (sensorSize?.width()?.div((2 * zoomLevel)))?.toInt() ?: 0
        val deltaY = (sensorSize?.height()?.div((2 * zoomLevel)))?.toInt() ?: 0

        return Rect(centerX - deltaX, centerY - deltaY, centerX + deltaX, centerY + deltaY)
    }

    /**
     * Get the supported ISO range for the camera.
     *
     * @return Range of supported ISO values.
     */
    // Function to get ISO range
    private fun getIsoRange(): Range<Int>? {
        return getCameraCharacteristics().get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
    }

    /**
     * Set the ISO sensitivity of the camera.
     *
     * @param isoValue The desired ISO value (should be within supported range).
     */
    // Function to set ISO sensitivity (isoValue should be within supported range)
    private fun setIso(isoValue: Int) {
        val isoRange = getIsoRange()
        if (isoRange != null && isoValue in isoRange) {
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoValue)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
            Log.d(TAG, "setIso: called")
        }
    }

    //shutter speed section
    /**
     * Get the supported exposure time range for the camera.
     *
     * @return Range of supported exposure times in nanoseconds.
     */
    // Function to get exposure time range
    private fun getExposureTimeRange(): Range<Long>? {
        return getCameraCharacteristics().get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
    }

    /**
     * SHUTTER SPEED
     * Set the exposure time of the camera.
     *
     * @param exposureTime The desired exposure time in nanoseconds.
     */
    // Function to set exposure time (exposureTime in nanoseconds)
    private fun setExposureTime(exposureTime: ExposureMode?) {
        val range = getExposureTimeRange()
        if (exposureTime == ExposureMode.AUTO) {
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        } else {
            val time = when (exposureTime) {

                ExposureMode.EXPOSURE_1_4000 -> {

                    range?.lower // Equivalent to 1/4000 second
                }

                ExposureMode.EXPOSURE_1_2000 -> {
                    range?.lower?.times(2) // Equivalent to 1/2000 second
                }

                ExposureMode.EXPOSURE_1_1000 -> {
                    range?.lower?.times(4) // Equivalent to 1/1000 second
                }

                ExposureMode.EXPOSURE_1_500 -> {
                    range?.lower?.times(8) // Equivalent to 1/500 second
                }

                ExposureMode.EXPOSURE_1_250 -> {
                    range?.lower?.times(16)// Equivalent to 1/250 second
                }

                ExposureMode.EXPOSURE_1_125 -> {
                    range?.lower?.times(32)// Equivalent to 1/125 second
                }

                ExposureMode.EXPOSURE_1_60 -> {
                    range?.lower?.times(64) // Equivalent to 1/60 second
                }

                ExposureMode.EXPOSURE_1_30 -> {
                    range?.lower?.times(128) // Equivalent to 1/30 second
                }

                ExposureMode.EXPOSURE_1_15 -> {
                    range?.upper?.div(256) // Equivalent to 1/15 second
                }

                ExposureMode.EXPOSURE_1_8 -> {
                    range?.lower?.times(256) // Equivalent to 1/8 second
                }

                ExposureMode.EXPOSURE_1_4 -> {
                    range?.upper?.div(128) // Equivalent to 1/4 second
                }

                ExposureMode.EXPOSURE_1_2 -> {
                    range?.upper?.div(64) // Equivalent to 1/2 second
                }

                ExposureMode.EXPOSURE_1 -> {
                    range?.upper?.div(32) // Equivalent to 1 second
                }

                ExposureMode.EXPOSURE_2 -> {
                    range?.upper?.div(16) // Equivalent to 2 seconds
                }

                ExposureMode.EXPOSURE_4 -> {
                    range?.upper?.div(8) // Equivalent to 4 seconds
                }

                ExposureMode.EXPOSURE_8 -> {
                    range?.upper?.div(4) // Equivalent to 8 seconds
                }

                ExposureMode.EXPOSURE_15 -> {
                    range?.upper?.div(2) // Equivalent to 15 seconds
                }

                ExposureMode.EXPOSURE_30 -> {
                    range?.upper // Equivalent to 30 seconds
                }

                else -> {
                    range?.upper?.div(256)
                }
            }
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, time)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
            Log.d(TAG, "setExposureTime: called $time range$range")

        }

    }


    /**
     * Set the white balance mode of the camera.
     *
     * @param whiteBalanceMode The desired white balance mode.
     */
    // Function to set white balance mode
    fun setWhiteBalanceMode(whiteBalanceMode: Int) {
        captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, whiteBalanceMode)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Set a custom white balance mode using color correction gains.
     *
     * @param redGain The gain for the red channel. 1-254
     * @param greenGain The gain for the green channel. 1-254
     * @param blueGain The gain for the blue channel. 1-254
     */
    private fun setCustomWhiteBalance(redGain: Float, greenGain: Float, blueGain: Float) {

        try {
            captureBuilder.set(
                CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF
            )
            captureBuilder.set(
                CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF
            )
            val gains = RggbChannelVector(redGain, greenGain, greenGain, blueGain)
            captureBuilder.set(CaptureRequest.COLOR_CORRECTION_GAINS, gains)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
            Log.d(TAG, "setCustomWhiteBalance: called")
        } catch (e: CameraAccessException) {
            Log.d(TAG, "setCustomWhiteBalance: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Set the exposure compensation value of the camera.
     *
     * @param exposureCompensationValue The desired exposure compensation value in EV units.
     * -20 to 20
     */
    // Function to set exposure compensation (exposureCompensationValue should be in EV units)
    private fun setExposureCompensation(exposureCompensationValue: Int) {
        val aeCompensationRange =
            getCameraCharacteristics().get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)

        if (aeCompensationRange != null && exposureCompensationValue in aeCompensationRange) {
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            captureBuilder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, exposureCompensationValue
            )
            Log.d(TAG, "setExposureCompensation: called $exposureCompensationValue")
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)

        }


    }

    /**
     * Turn on the flash of the camera.
     */
    fun turnOnFlash() {
        captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Turn off the flash of the camera.
     */
    // Function to turn off the flash
    fun turnOffFlash() {
        captureBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Perform custom focus at a specific area on the camera preview.
     *
     * @param x The x-coordinate of the top-left corner of the focus area.
     * @param y The y-coordinate of the top-left corner of the focus area.
     * @param width The width of the focus area.
     * @param height The height of the focus area.
     */
    fun setCustomFocus(x: Int, y: Int, width: Int, height: Int) {
        val rect = Rect(x, y, x + height, y + width)
        val meteringRectangle = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX)

        val meteringRectangles = arrayOf(meteringRectangle)

        captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangles)
        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun setCustomFocusPercent(percent: Float) {
        try {
            val characteristics = getCameraCharacteristics()
            val sensorSize =
                characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)

            val x = (sensorSize?.width()!! * percent / 100).toInt()
            val y = (sensorSize.height() * percent / 100).toInt()

            val halfWidth =
                (sensorSize.width() * 0.1).toInt()  // Assuming 10% of the width for focus area
            val halfHeight =
                (sensorSize.height() * 0.1).toInt()  // Assuming 10% of the height for focus area

            val rect = Rect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight)

            val meteringRectangle = MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX)

            val meteringRectangles = arrayOf(meteringRectangle)

            // Disable auto focus
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)

            // Set focus distance manually
            val minFocusDistance =
                characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val focusDistance = minFocusDistance + (percent / 100) * (1 - minFocusDistance)
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)

            captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, meteringRectangles)
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     * Set metering regions for the camera.
     *
     * @param regions Array of MeteringRectangle objects representing the regions.
     */
    // Function to set metering regions
    fun setMeteringRegions(regions: Array<MeteringRectangle>) {
        captureBuilder.set(CaptureRequest.CONTROL_AF_REGIONS, regions)
        captureBuilder.set(CaptureRequest.CONTROL_AE_REGIONS, regions)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Set the color effect mode of the camera.
     *
     * @param colorEffectMode The desired color effect mode.
     */
    // Function to set color effect mode
    fun setColorEffectMode(colorEffectMode: Int) {
        captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, colorEffectMode)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Set the scene mode of the camera.
     *
     * @param sceneMode The desired scene mode.
     */
    // Function to set scene mode
    fun setSceneMode(sceneMode: Int) {
        captureBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, sceneMode)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    /**
     * Enable or disable face detection.
     *
     * @param enabled Boolean indicating whether face detection should be enabled.
     */
    // Function to enable/disable face detection
    fun setFaceDetectionEnabled(enabled: Boolean) {
        captureBuilder.set(
            CaptureRequest.STATISTICS_FACE_DETECT_MODE,
            if (enabled) CaptureRequest.STATISTICS_FACE_DETECT_MODE_FULL else CaptureRequest.STATISTICS_FACE_DETECT_MODE_OFF
        )
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }


}
