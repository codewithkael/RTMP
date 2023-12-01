import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_OFF
import android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.RggbChannelVector
import android.hardware.camera2.params.TonemapCurve
import android.util.Log
import android.util.Range
import com.codewithkael.rtmp.utils.CameraInfoModel
import com.codewithkael.rtmp.utils.ExposureMode
import com.codewithkael.rtmp.utils.fromPercent
import com.haishinkit.view.HkSurfaceView
import java.lang.StrictMath.pow
import kotlin.math.max
import kotlin.math.min

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
        val availableApertures =
            getCameraCharacteristics().get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
        Log.d(TAG, "updateCameraInfo aaa : ${availableApertures?.toList()}")
        try {
//            setCustomWhiteBalance(info.red, info.green, info.blue)

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


            val gama = if (info.gamma <= 0.1f) {
                0.1f
            } else if (info.gamma >= 5.0f) {
                5.0f
            } else {
                info.gamma
            }

            val contrast = if (info.contrast <= 0.1f) {
                0.1f
            } else if (info.contrast >= 2.0f) {
                2.0f
            } else {
                info.contrast
            }
            adjustGammaAndContrast2(gama, contrast)
//            if (info.flashLight) turnOnFlash() else turnOffFlash()

            if (info.isAutoWhiteBalance) {
                setAutoWhiteBalanceOn()
            } else {
                setCustomWhiteBalance(info.red, info.green, info.blue)
            }

            //            //focus mode
            val focus = if (info.focusPercent <= 0.1f) {
                0.1f
            } else {
                info.focusPercent
            }
//            turnOnFlash()
            if (info.flashLight) {
                setAutoFocusForContinousOn()
            } else {
                setCustomFocusPercent2(focus * 100)
            }


        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setAutoFocusForContinousOn() {
        captureBuilder.set(
            CaptureRequest.CONTROL_AF_MODE,
//            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE // Use CONTROL_AF_MODE_CONTINUOUS_VIDEO for video
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO // Use CONTROL_AF_MODE_CONTINUOUS_VIDEO for video
        )
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun setAutoFocusForContinousOff() {
        captureBuilder.set(
            CaptureRequest.CONTROL_EFFECT_MODE,
            CaptureRequest.CONTROL_EFFECT_MODE_OFF
        )
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
    }

    private fun setAutoWhiteBalanceOn() {
        captureBuilder.set(
            CaptureRequest.COLOR_CORRECTION_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO
        )
        captureBuilder.set(
            CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_AUTO
        )

        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)

    }

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
//            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF)

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
//            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_OFF)
        captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_OFF)
        captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, time)
        captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        Log.d(TAG, "setExposureTime: called $time range$range")

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

        try {
//            val convertedVersion = exposureCompensationValue.toFloat().fromPercent(Range(aeCompensationRange!!.lower-1,aeCompensationRange.upper+1))
            val convertedVersion = mapNumber(
                exposureCompensationValue, -20, 20,
                aeCompensationRange!!.lower, aeCompensationRange.upper
            )
            Log.d(TAG, "setExposureCompensation: range chosen $convertedVersion")
//            captureBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CONTROL_AE_MODE_ON)

            captureBuilder.set(
                CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, convertedVersion
            )
            Log.d(TAG, "setExposureCompensation: called $convertedVersion")
            captureSession.setRepeatingRequest(captureBuilder.build(), null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun mapNumber(
        value: Int,
        originalRangeStart: Int,
        originalRangeEnd: Int,
        targetRangeStart: Int,
        targetRangeEnd: Int
    ): Int {
        // Check if the input number is within the initial range
        if (value !in originalRangeStart..originalRangeEnd) {
            throw IllegalArgumentException("Input number is outside the initial range")
        }
        return targetRangeStart + (value - originalRangeStart) * (targetRangeEnd - targetRangeStart) / (originalRangeEnd - originalRangeStart)
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


    private fun setCustomFocusPercent2(percent: Float) {
        try {
            val characteristics = getCameraCharacteristics()
            val minFocusDistance =
                characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                    ?: return

            // Convert the input percent to a focus distance value
            // 0% corresponds to infinity, and 100% to the minimum focus distance
            val focusDistance = minFocusDistance * (1 - percent / 100)

            // Set the manual focus distance
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            captureBuilder.set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)

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

    private fun adjustGammaAndContrast(gamma: Float, contrast: Float) {
        // Adjust Gamma
        val MAX_GAMMA = 5.0f
        val MIN_GAMMA = 0.1f
        val adjustedGamma = max(MIN_GAMMA, min(gamma, MAX_GAMMA))

        // Adjust Contrast
        val MAX_CONTRAST = 2.0f
        val MIN_CONTRAST = 0.0f
        val adjustedContrast = max(MIN_CONTRAST, min(contrast, MAX_CONTRAST))

        val mid = 0.5f
        val size = 256
        val curve = FloatArray(size * 2)
        for (i in 0 until size) {
            val originalValue = i / 255.0f

            // Apply Gamma Adjustment
            val gammaCorrectedValue =
                pow(originalValue.toDouble(), (1.0f / adjustedGamma).toDouble()).toFloat()

            // Apply Contrast Adjustment
            val contrastCorrectedValue = if (gammaCorrectedValue < mid) {
                (pow(
                    (gammaCorrectedValue / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            } else {
                (1 - pow(
                    ((1 - gammaCorrectedValue) / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            }

            curve[i * 2] = originalValue
            curve[i * 2 + 1] = contrastCorrectedValue
        }

        val tonemapCurve = TonemapCurve(curve, curve, curve)
        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
    }

    private fun adjustGammaAndContrast2(gamma: Float, contrast: Float) {
        val cameraCharacteristics = getCameraCharacteristics()
        val (gammaRange, contrastRange) = setGammaAndContrastRanges(cameraCharacteristics)

        // Adjust Gamma within the range
        val adjustedGamma = max(gammaRange.start, min(gamma, gammaRange.endInclusive))

        // Adjust Contrast within the range
        val adjustedContrast = max(contrastRange.start, min(contrast, contrastRange.endInclusive))

        val mid = 0.5f
        val size = 256
        val curve = FloatArray(size * 2)
        for (i in 0 until size) {
            val originalValue = i / 255.0f

            // Apply Gamma Adjustment
            val gammaCorrectedValue =
                pow(originalValue.toDouble(), (1.0f / adjustedGamma).toDouble()).toFloat()

            // Apply Contrast Adjustment
            val contrastCorrectedValue = if (gammaCorrectedValue < mid) {
                (pow(
                    (gammaCorrectedValue / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            } else {
                (1 - pow(
                    ((1 - gammaCorrectedValue) / mid).toDouble(),
                    adjustedContrast.toDouble()
                ) * mid).toFloat()
            }

            curve[i * 2] = originalValue
            curve[i * 2 + 1] = contrastCorrectedValue
        }

        val tonemapCurve = TonemapCurve(curve, curve, curve)
        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
    }

    fun getCameraCapabilities(context: Context, cameraId: String): CameraCharacteristics {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return cameraManager.getCameraCharacteristics(cameraId)
    }

    fun setGammaAndContrastRanges(cameraCharacteristics: CameraCharacteristics): Pair<ClosedFloatingPointRange<Float>, ClosedFloatingPointRange<Float>> {
        val maxCurvePoints =
            cameraCharacteristics.get(CameraCharacteristics.TONEMAP_MAX_CURVE_POINTS)
        val SOME_THRESHOLD = 10 // Example threshold, adjust based on your requirements

        val gammaRange = if (maxCurvePoints != null && maxCurvePoints >= SOME_THRESHOLD) {
            0.1f..5.0f
        } else {
            0.5f..2.0f
        }

        val contrastRange = if (maxCurvePoints != null && maxCurvePoints >= SOME_THRESHOLD) {
            0.0f..2.0f
        } else {
            0.2f..1.5f
        }

        return Pair(gammaRange, contrastRange)
    }


//    fun adjustGamma(gamma: Float) {
//        val MAX_GAMMA = 5.0f
//        val MIN_GAMMA = 0.1f
//        val adjustedGamma = max(MIN_GAMMA, min(gamma, MAX_GAMMA))
//
//        val size = 256
//        val curve = FloatArray(size * 2)
//        for (i in 0 until size) {
//            curve[i * 2] = i / 255.0f
//            curve[i * 2 + 1] =
//                pow(curve[i * 2].toDouble(), (1.0f / adjustedGamma).toDouble()).toFloat()
//        }
//
//        val tonemapCurve = TonemapCurve(curve, curve, curve)
//        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
//        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
//    }
//
//    fun adjustContrast(contrast: Float) {
//        val MAX_CONTRAST = 2.0f
//        val MIN_CONTRAST = 0.0f
//        val adjustedContrast = max(MIN_CONTRAST, min(contrast, MAX_CONTRAST))
//
//        val mid = 0.5f
//        val size = 256
//        val curve = FloatArray(size * 2)
//        for (i in 0 until size) {
//            val value = i / 255.0f
//            curve[i * 2] = value
//            if (value < mid) {
//                curve[i * 2 + 1] =
//                    (pow((value / mid).toDouble(), adjustedContrast.toDouble()) * mid).toFloat()
//            } else {
//                curve[i * 2 + 1] = (1 - pow(
//                    ((1 - value) / mid).toDouble(),
//                    adjustedContrast.toDouble()
//                ) * mid).toFloat()
//            }
//        }
//
//        val tonemapCurve = TonemapCurve(curve, curve, curve)
//        captureBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_CONTRAST_CURVE)
//        captureBuilder.set(CaptureRequest.TONEMAP_CURVE, tonemapCurve)
//    }


}
