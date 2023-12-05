package cachet.plugins.health

// import androidx.compose.runtime.mutableStateOf

// Health Connect
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectClient.Companion.ACTION_HEALTH_CONNECT_SETTINGS
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.*
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.Registrar
import kotlinx.coroutines.*
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.*

const val HEALTH_CONNECT_RESULT_CODE = 16969
const val CHANNEL_NAME = "flutter_health"

class HealthConnectPlugin(private var channel: MethodChannel? = null) :
    MethodCallHandler,
    ActivityResultListener,
    Result,
    ActivityAware,
    FlutterPlugin {
    private var mResult: Result? = null
    private var handler: Handler? = null
    private var activity: Activity? = null
    private var context: Context? = null
    private var threadPoolExecutor: ExecutorService? = null
    private var useHealthConnectIfAvailable: Boolean = false
    private lateinit var healthConnectClient: HealthConnectClient
    private lateinit var scope: CoroutineScope

    private var BODY_FAT_PERCENTAGE = "BODY_FAT_PERCENTAGE"
    private var HEIGHT = "HEIGHT"
    private var WEIGHT = "WEIGHT"
    private var STEPS = "STEPS"
    private var AGGREGATE_STEP_COUNT = "AGGREGATE_STEP_COUNT"
    private var ACTIVE_ENERGY_BURNED = "ACTIVE_ENERGY_BURNED"
    private var HEART_RATE = "HEART_RATE"
    private var BODY_TEMPERATURE = "BODY_TEMPERATURE"
    private var BLOOD_PRESSURE_SYSTOLIC = "BLOOD_PRESSURE_SYSTOLIC"
    private var BLOOD_PRESSURE_DIASTOLIC = "BLOOD_PRESSURE_DIASTOLIC"
    private var BLOOD_OXYGEN = "BLOOD_OXYGEN"
    private var BLOOD_GLUCOSE = "BLOOD_GLUCOSE"
    private var MOVE_MINUTES = "MOVE_MINUTES"
    private var DISTANCE_DELTA = "DISTANCE_DELTA"
    private var WATER = "WATER"
    private var RESTING_HEART_RATE = "RESTING_HEART_RATE"
    private var BASAL_ENERGY_BURNED = "BASAL_ENERGY_BURNED"
    private var FLIGHTS_CLIMBED = "FLIGHTS_CLIMBED"
    private var RESPIRATORY_RATE = "RESPIRATORY_RATE"

    // TODO support unknown?
    private var SLEEP_ASLEEP = "SLEEP_ASLEEP"
    private var SLEEP_AWAKE = "SLEEP_AWAKE"
    private var SLEEP_IN_BED = "SLEEP_IN_BED"
    private var SLEEP_SESSION = "SLEEP_SESSION"
    private var SLEEP_LIGHT = "SLEEP_LIGHT"
    private var SLEEP_DEEP = "SLEEP_DEEP"
    private var SLEEP_REM = "SLEEP_REM"
    private var SLEEP_OUT_OF_BED = "SLEEP_OUT_OF_BED"
    private var WORKOUT = "WORKOUT"

    // 方法
    var callMethod = ""

    // TODO: Update with new workout types when Health Connect becomes the standard.
    val workoutTypeMapHealthConnect = mapOf(
        "AMERICAN_FOOTBALL" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN,
        "AUSTRALIAN_FOOTBALL" to ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AUSTRALIAN,
        "BADMINTON" to ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON,
        "BASEBALL" to ExerciseSessionRecord.EXERCISE_TYPE_BASEBALL,
        "BASKETBALL" to ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
        "BIKING" to ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
        "BOXING" to ExerciseSessionRecord.EXERCISE_TYPE_BOXING,
        "CALISTHENICS" to ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS,
        "CRICKET" to ExerciseSessionRecord.EXERCISE_TYPE_CRICKET,
        "DANCING" to ExerciseSessionRecord.EXERCISE_TYPE_DANCING,
        "ELLIPTICAL" to ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL,
        "FENCING" to ExerciseSessionRecord.EXERCISE_TYPE_FENCING,
        "FRISBEE_DISC" to ExerciseSessionRecord.EXERCISE_TYPE_FRISBEE_DISC,
        "GOLF" to ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
        "GUIDED_BREATHING" to ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
        "GYMNASTICS" to ExerciseSessionRecord.EXERCISE_TYPE_GYMNASTICS,
        "HANDBALL" to ExerciseSessionRecord.EXERCISE_TYPE_HANDBALL,
        "HIGH_INTENSITY_INTERVAL_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
        "HIKING" to ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
        "ICE_SKATING" to ExerciseSessionRecord.EXERCISE_TYPE_ICE_SKATING,
        "MARTIAL_ARTS" to ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
        "PARAGLIDING" to ExerciseSessionRecord.EXERCISE_TYPE_PARAGLIDING,
        "PILATES" to ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
        "RACQUETBALL" to ExerciseSessionRecord.EXERCISE_TYPE_RACQUETBALL,
        "ROCK_CLIMBING" to ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
        "ROWING" to ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
        "ROWING_MACHINE" to ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
        "RUGBY" to ExerciseSessionRecord.EXERCISE_TYPE_RUGBY,
        "RUNNING_TREADMILL" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
        "RUNNING" to ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
        "SAILING" to ExerciseSessionRecord.EXERCISE_TYPE_SAILING,
        "SCUBA_DIVING" to ExerciseSessionRecord.EXERCISE_TYPE_SCUBA_DIVING,
        "SKATING" to ExerciseSessionRecord.EXERCISE_TYPE_SKATING,
        "SKIING" to ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
        "SNOWBOARDING" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
        "SNOWSHOEING" to ExerciseSessionRecord.EXERCISE_TYPE_SNOWSHOEING,
        "SOFTBALL" to ExerciseSessionRecord.EXERCISE_TYPE_SOFTBALL,
        "SQUASH" to ExerciseSessionRecord.EXERCISE_TYPE_SQUASH,
        "STAIR_CLIMBING_MACHINE" to ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE,
        "STAIR_CLIMBING" to ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        "STRENGTH_TRAINING" to ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        "SURFING" to ExerciseSessionRecord.EXERCISE_TYPE_SURFING,
        "SWIMMING_OPEN_WATER" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
        "SWIMMING_POOL" to ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        "TABLE_TENNIS" to ExerciseSessionRecord.EXERCISE_TYPE_TABLE_TENNIS,
        "TENNIS" to ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
        "VOLLEYBALL" to ExerciseSessionRecord.EXERCISE_TYPE_VOLLEYBALL,
        "WALKING" to ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
        "WATER_POLO" to ExerciseSessionRecord.EXERCISE_TYPE_WATER_POLO,
        "WEIGHTLIFTING" to ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        "WHEELCHAIR" to ExerciseSessionRecord.EXERCISE_TYPE_WHEELCHAIR,
        "YOGA" to ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
        "OTHER" to ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
    )

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        channel?.setMethodCallHandler(this)
        context = flutterPluginBinding.applicationContext
        threadPoolExecutor = Executors.newFixedThreadPool(4)
        checkAvailability()
        initHealthConnectClient()
//        if (healthConnectAvailable) {
//            healthConnectClient =
//                HealthConnectClient.getOrCreate(flutterPluginBinding.applicationContext)
//        }
    }

    fun initHealthConnectClient() {
        if (healthConnectAvailable && !::healthConnectClient.isInitialized && context != null) {
            healthConnectClient =
                HealthConnectClient.getOrCreate(context!!)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = null
        activity = null
        threadPoolExecutor!!.shutdown()
        threadPoolExecutor = null
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), CHANNEL_NAME)
            val plugin = HealthConnectPlugin(channel)
            registrar.addActivityResultListener(plugin)
            channel.setMethodCallHandler(plugin)
        }
    }

    override fun success(p0: Any?) {
        handler?.post { mResult?.success(p0) }
    }

    override fun notImplemented() {
        handler?.post { mResult?.notImplemented() }
    }

    override fun error(
        errorCode: String,
        errorMessage: String?,
        errorDetails: Any?,
    ) {
        handler?.post { mResult?.error(errorCode, errorMessage, errorDetails) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == HEALTH_CONNECT_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    if (data.extras?.containsKey("request_blocked") == true) {
                        Log.i(
                            "FLUTTER_HEALTH",
                            "Access Denied (to Health Connect) due to too many requests!"
                        )
                        if (callMethod == "requestAuthorization") {
                            mResult?.success("request_blocked")
                        } else {
                            mResult?.success(false)
                        }
                        return false
                    }
                }
                Log.i("FLUTTER_HEALTH", "Access Granted (to Health Connect)!")
                mResult?.success(true)
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.i("FLUTTER_HEALTH", "Access Denied (to Health Connect)!")
                mResult?.success(false)
            }
        }
        return false
    }

    /**
     * Delete records of the given type in the time range
     */
    private fun delete(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            deleteHCData(call, result)
            return
        }else{
            result.success(false)
            return
        }
    }

    /**
     * Save a Blood Pressure measurement with systolic and diastolic values
     */
    private fun writeBloodPressure(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            writeBloodPressureHC(call, result)
            return
        }else{
            result.success(false)
            return
        }
    }

    /**
     * Save a data type in Google Fit
     */
    private fun writeData(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            writeHCData(call, result)
            return
        }else{
            result.success(false)
            return
        }
    }

    /**
     * Save the blood oxygen saturation, in Google Fit with the supplemental flow rate, in HealthConnect without
     */
    private fun writeBloodOxygen(call: MethodCall, result: Result) {
        // Health Connect does not support supplemental flow rate, thus it is ignored
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            writeHCData(call, result)
            return
        }else{
            result.success(false)
            return
        }
    }

    /**
     * Save a Workout session with options for distance and calories expended
     */
    private fun writeWorkoutData(call: MethodCall, result: Result) {

        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            writeWorkoutHCData(call, result)
            return
        } else {
            result.success(false)
            return
        }
    }

    /**
     * Get all datapoints of the DataType within the given time range
     */
    private fun getData(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            getHCData(call, result)
            return
        } else {
            result.success(null)
            return
        }
    }

    private fun hasPermissions(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            hasPermissionsHC(call, result)
            return
        } else {
            result.success(false)
            return
        }
    }

    /**
     * Requests authorization for the HealthDataTypes
     * with the the READ or READ_WRITE permission type.
     */
    private fun requestAuthorization(call: MethodCall, result: Result) {
        if (context == null) {
            result.success(false)
            return
        }
        mResult = result

        checkAvailability()
        initHealthConnectClient()

        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            requestAuthorizationHC(call, result)
            return
        } else {
            result.success(false)
            return
        }

    }

    /**
     * Revokes access to Google Fit using the `disableFit`-method.
     *
     * Note: Using the `revokeAccess` creates a bug on android
     * when trying to reapply for permissions afterwards, hence
     * `disableFit` was used.
     */
    private fun revokePermissions(call: MethodCall, result: Result) {
        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            scope.launch {
                healthConnectClient.permissionController.revokeAllPermissions()
            }
            result.notImplemented()
            return
        }
        if (context == null) {
            result.success(false)
            return
        }
        result.success(false)
    }


    private fun getTotalStepsInInterval(call: MethodCall, result: Result) {
        val start = call.argument<Long>("startTime")!!
        val end = call.argument<Long>("endTime")!!

        if (useHealthConnectIfAvailable && healthConnectAvailable) {
            getStepsHealthConnect(start, end, result)
            return
        }

        return
    }

    private fun getStepsHealthConnect(start: Long, end: Long, result: Result) = scope.launch {
        try {
            val startInstant = Instant.ofEpochMilli(start)
            val endInstant = Instant.ofEpochMilli(end)
            val response = healthConnectClient.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant),
                ),
            )
            // The result may be null if no data is available in the time range.
            val stepsInInterval = response[StepsRecord.COUNT_TOTAL] ?: 0L
            Log.i("FLUTTER_HEALTH::SUCCESS", "returning $stepsInInterval steps")
            result.success(stepsInInterval)
        } catch (e: Exception) {
            Log.i("FLUTTER_HEALTH::ERROR", "unable to return steps")
            result.success(null)
        }
    }

    /**
     *  Handle calls from the MethodChannel
     */
    override fun onMethodCall(call: MethodCall, result: Result) {
        callMethod = call.method
        Log.i("tag--","method name ${call.method}")
        when (call.method) {
            "useHealthConnectIfAvailable" -> useHealthConnectIfAvailable(call, result)
            "hasPermissions" -> hasPermissions(call, result)
            "requestAuthorization" -> requestAuthorization(call, result)
            "revokePermissions" -> revokePermissions(call, result)
            "getData" -> getData(call, result)
            "writeData" -> writeData(call, result)
            "delete" -> delete(call, result)
            "getTotalStepsInInterval" -> getTotalStepsInInterval(call, result)
            "writeWorkoutData" -> writeWorkoutData(call, result)
            "writeBloodPressure" -> writeBloodPressure(call, result)
            "writeBloodOxygen" -> writeBloodOxygen(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if (channel == null) {
            return
        }
        binding.addActivityResultListener(this)
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        if (channel == null) {
            return
        }
        activity = null
    }

    /**
     * HEALTH CONNECT BELOW
     */
    var healthConnectAvailable = false
    var healthConnectStatus = HealthConnectClient.SDK_UNAVAILABLE

    fun checkAvailability() {
        healthConnectStatus = HealthConnectClient.getSdkStatus(context!!)
        healthConnectAvailable = healthConnectStatus == HealthConnectClient.SDK_AVAILABLE
    }

    fun useHealthConnectIfAvailable(call: MethodCall, result: Result) {
        useHealthConnectIfAvailable = true
        result.success(null)
    }

    private fun hasPermissionsHC(call: MethodCall, result: Result) {
        val args = call.arguments as HashMap<*, *>
        val types = (args["types"] as? ArrayList<*>)?.filterIsInstance<String>()!!
        val permissions = (args["permissions"] as? ArrayList<*>)?.filterIsInstance<Int>()!!

        var permList = mutableListOf<String>()
        for ((i, typeKey) in types.withIndex()) {
            val access = permissions[i]!!
            val dataType = MapToHCType[typeKey]!!
            if (access == 0) {
                permList.add(
                    HealthPermission.getReadPermission(dataType),
                )
            } else {

                permList.add(HealthPermission.getWritePermission(dataType))
//                permList.addAll(
//                    listOf(
////                        HealthPermission.getReadPermission(dataType),
//                        HealthPermission.getWritePermission(dataType),
//                    ),
//                )
            }
            // Workout also needs distance and total energy burned too
            if (typeKey == WORKOUT) {
                if (access == 0) {
                    permList.add(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
//                    permList.addAll(
//                        listOf(
////                            HealthPermission.getReadPermission(DistanceRecord::class),
//                            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
//                        ),
//                    )
                } else {
                    permList.addAll(
                        listOf(
//                            HealthPermission.getReadPermission(DistanceRecord::class),
//                            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
//                            HealthPermission.getWritePermission(DistanceRecord::class),
                            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
                        ),
                    )
                }
            }
        }
        scope.launch {
            var grantedPermissions = false
            try {
                grantedPermissions =
                    healthConnectClient.permissionController.getGrantedPermissions()
                        .containsAll(permList)

            } catch (e: IllegalStateException) {
                Log.i("health info", "fail: ${e.message}")
            }
            result.success(
                grantedPermissions
            )
        }
    }

    private fun requestAuthorizationHC(call: MethodCall, result: Result) {
        val args = call.arguments as HashMap<*, *>
        val types = (args["types"] as? ArrayList<*>)?.filterIsInstance<String>()!!
        val permissions = (args["permissions"] as? ArrayList<*>)?.filterIsInstance<Int>()!!

        var permList = mutableListOf<String>()
        for ((i, typeKey) in types.withIndex()) {
            val access = permissions[i]!!
            val dataType = MapToHCType[typeKey]!!
            if (access == 0) {
                permList.add(
                    HealthPermission.getReadPermission(dataType),
                )
            } else {
                permList.add(HealthPermission.getWritePermission(dataType))
//                permList.addAll(
//                    listOf(
//                        HealthPermission.getReadPermission(dataType),
//                        HealthPermission.getWritePermission(dataType),
//                    ),
//                )
            }
            // Workout also needs distance and total energy burned too
            if (typeKey == WORKOUT) {
                if (access == 0) {
                    permList.add(HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class))
//                    permList.addAll(
//                        listOf(
//                            HealthPermission.getReadPermission(DistanceRecord::class),
//                            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
//                        ),
//                    )
                } else {
                    permList.addAll(
                        listOf(
//                            HealthPermission.getReadPermission(DistanceRecord::class),
//                            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
//                            HealthPermission.getWritePermission(DistanceRecord::class),
                            HealthPermission.getWritePermission(TotalCaloriesBurnedRecord::class),
                        ),
                    )
                }
            }
        }
        val contract = PermissionController.createRequestPermissionResultContract()
        val intent = contract.createIntent(activity!!, permList.toSet())
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.TIRAMISU) {
            // 在此处添加您想要执行的代码，如果SDK系统大于13
            intent.action = ACTION_HEALTH_CONNECT_SETTINGS
        }
        activity!!.startActivityForResult(intent, HEALTH_CONNECT_RESULT_CODE)
    }

    fun getHCData(call: MethodCall, result: Result) {
        val dataType = call.argument<String>("dataTypeKey")!!
        val startTime = Instant.ofEpochMilli(call.argument<Long>("startTime")!!)
        val endTime = Instant.ofEpochMilli(call.argument<Long>("endTime")!!)
        val healthConnectData = mutableListOf<Map<String, Any?>>()
        scope.launch {
            MapToHCType[dataType]?.let { classType ->
                val request = ReadRecordsRequest(
                    recordType = classType,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                )
                val response = healthConnectClient.readRecords(request)

                // Workout needs distance and total calories burned too
                if (dataType == WORKOUT) {
                    for (rec in response.records) {
                        val record = rec as ExerciseSessionRecord
                        val distanceRequest = healthConnectClient.readRecords(
                            ReadRecordsRequest(
                                recordType = DistanceRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(
                                    record.startTime,
                                    record.endTime,
                                ),
                            ),
                        )
                        var totalDistance = 0.0
                        for (distanceRec in distanceRequest.records) {
                            totalDistance += distanceRec.distance.inMeters
                        }

                        val energyBurnedRequest = healthConnectClient.readRecords(
                            ReadRecordsRequest(
                                recordType = TotalCaloriesBurnedRecord::class,
                                timeRangeFilter = TimeRangeFilter.between(
                                    record.startTime,
                                    record.endTime,
                                ),
                            ),
                        )
                        var totalEnergyBurned = 0.0
                        for (energyBurnedRec in energyBurnedRequest.records) {
                            totalEnergyBurned += energyBurnedRec.energy.inKilocalories
                        }

                        // val metadata = (rec as Record).metadata
                        // Add final datapoint
                        healthConnectData.add(
                            // mapOf(
                            mapOf<String, Any?>(
                                "workoutActivityType" to (
                                        workoutTypeMapHealthConnect.filterValues { it == record.exerciseType }.keys.firstOrNull()
                                            ?: "OTHER"
                                        ),
                                "totalDistance" to if (totalDistance == 0.0) null else totalDistance,
                                "totalDistanceUnit" to "METER",
                                "totalEnergyBurned" to if (totalEnergyBurned == 0.0) null else totalEnergyBurned,
                                "totalEnergyBurnedUnit" to "KILOCALORIE",
                                "unit" to "MINUTES",
                                "date_from" to rec.startTime.toEpochMilli(),
                                "date_to" to rec.endTime.toEpochMilli(),
                                "source_id" to "",
                                "source_name" to record.metadata.dataOrigin.packageName,
                            ),
                        )
                    }
                    // Filter sleep stages for requested stage
                } else if (classType == SleepStageRecord::class) {
                    for (rec in response.records) {
                        if (rec is SleepStageRecord) {
                            if (dataType == MapSleepStageToType[rec.stage]) {
                                healthConnectData.addAll(convertRecord(rec, dataType))
                            }
                        }
                    }
                } else {
                    for (rec in response.records) {
                        healthConnectData.addAll(convertRecord(rec, dataType))
                    }
                }
            }
            Handler(context!!.mainLooper).run { result.success(healthConnectData) }
        }
    }

    // TODO: Find alternative to SOURCE_ID or make it nullable?
    fun convertRecord(record: Any, dataType: String): List<Map<String, Any>> {
        val metadata = (record as Record).metadata
        when (record) {
            is WeightRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.weight.inKilograms,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is HeightRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.height.inMeters,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is BodyFatRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.percentage.value,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is StepsRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.count,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is ActiveCaloriesBurnedRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.energy.inKilocalories,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is HeartRateRecord -> return record.samples.map {
                mapOf<String, Any>(
                    "value" to it.beatsPerMinute,
                    "date_from" to it.time.toEpochMilli(),
                    "date_to" to it.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                )
            }

            is BodyTemperatureRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.temperature.inCelsius,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is BloodPressureRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to if (dataType == BLOOD_PRESSURE_DIASTOLIC) record.diastolic.inMillimetersOfMercury else record.systolic.inMillimetersOfMercury,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is OxygenSaturationRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.percentage.value,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is BloodGlucoseRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.level.inMilligramsPerDeciliter,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is DistanceRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.distance.inMeters,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is HydrationRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.volume.inLiters,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is SleepSessionRecord -> return listOf(
                mapOf<String, Any>(
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "value" to ChronoUnit.MINUTES.between(record.startTime, record.endTime),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is SleepStageRecord -> return listOf(
                mapOf<String, Any>(
                    "stage" to record.stage,
                    "value" to ChronoUnit.MINUTES.between(record.startTime, record.endTime),
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                ),
            )

            is RestingHeartRateRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.beatsPerMinute,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                )
            )

            is BasalMetabolicRateRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.basalMetabolicRate.inKilocaloriesPerDay,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                )
            )

            is FloorsClimbedRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.floors,
                    "date_from" to record.startTime.toEpochMilli(),
                    "date_to" to record.endTime.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                )
            )

            is RespiratoryRateRecord -> return listOf(
                mapOf<String, Any>(
                    "value" to record.rate,
                    "date_from" to record.time.toEpochMilli(),
                    "date_to" to record.time.toEpochMilli(),
                    "source_id" to "",
                    "source_name" to metadata.dataOrigin.packageName,
                )
            )
            // is ExerciseSessionRecord -> return listOf(mapOf<String, Any>("value" to ,
            //                                             "date_from" to ,
            //                                             "date_to" to ,
            //                                             "source_id" to "",
            //                                             "source_name" to metadata.dataOrigin.packageName))
            else -> throw IllegalArgumentException("Health data type not supported") // TODO: Exception or error?
        }
    }

    fun writeHCData(call: MethodCall, result: Result) {
        val type = call.argument<String>("dataTypeKey")!!
        val startTime = call.argument<Long>("startTime")!!
        val endTime = call.argument<Long>("endTime")!!
        val value = call.argument<Double>("value")!!
        val record = when (type) {
            BODY_FAT_PERCENTAGE -> BodyFatRecord(
                time = Instant.ofEpochMilli(startTime),
                percentage = Percentage(value),
                zoneOffset = null,
            )

            HEIGHT -> HeightRecord(
                time = Instant.ofEpochMilli(startTime),
                height = Length.meters(value),
                zoneOffset = null,
            )

            WEIGHT -> WeightRecord(
                time = Instant.ofEpochMilli(startTime),
                weight = Mass.kilograms(value),
                zoneOffset = null,
            )

            STEPS -> StepsRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                count = value.toLong(),
                startZoneOffset = null,
                endZoneOffset = null,
            )

            ACTIVE_ENERGY_BURNED -> ActiveCaloriesBurnedRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                energy = Energy.kilocalories(value),
                startZoneOffset = null,
                endZoneOffset = null,
            )

            HEART_RATE -> HeartRateRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                samples = listOf<HeartRateRecord.Sample>(
                    HeartRateRecord.Sample(
                        time = Instant.ofEpochMilli(startTime),
                        beatsPerMinute = value.toLong(),
                    ),
                ),
                startZoneOffset = null,
                endZoneOffset = null,
            )

            BODY_TEMPERATURE -> BodyTemperatureRecord(
                time = Instant.ofEpochMilli(startTime),
                temperature = Temperature.celsius(value),
                zoneOffset = null,
            )

            BLOOD_OXYGEN -> OxygenSaturationRecord(
                time = Instant.ofEpochMilli(startTime),
                percentage = Percentage(value),
                zoneOffset = null,
            )

            BLOOD_GLUCOSE -> BloodGlucoseRecord(
                time = Instant.ofEpochMilli(startTime),
                level = BloodGlucose.milligramsPerDeciliter(value),
                zoneOffset = null,
            )

            DISTANCE_DELTA -> DistanceRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                distance = Length.meters(value),
                startZoneOffset = null,
                endZoneOffset = null,
            )

            WATER -> HydrationRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                volume = Volume.liters(value),
                startZoneOffset = null,
                endZoneOffset = null,
            )

            SLEEP_ASLEEP -> SleepStageRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
                stage = SleepStageRecord.STAGE_TYPE_SLEEPING,
            )

            SLEEP_LIGHT -> SleepStageRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
                stage = SleepStageRecord.STAGE_TYPE_LIGHT,
            )

            SLEEP_DEEP -> SleepStageRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
                stage = SleepStageRecord.STAGE_TYPE_DEEP,
            )

            SLEEP_REM -> SleepStageRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
                stage = SleepStageRecord.STAGE_TYPE_REM,
            )

            SLEEP_OUT_OF_BED -> SleepStageRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
                stage = SleepStageRecord.STAGE_TYPE_OUT_OF_BED,
            )

            SLEEP_AWAKE -> SleepStageRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
                stage = SleepStageRecord.STAGE_TYPE_AWAKE,
            )


            SLEEP_SESSION -> SleepSessionRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                startZoneOffset = null,
                endZoneOffset = null,
            )

            RESTING_HEART_RATE -> RestingHeartRateRecord(
                time = Instant.ofEpochMilli(startTime),
                beatsPerMinute = value.toLong(),
                zoneOffset = null,
            )

            BASAL_ENERGY_BURNED -> BasalMetabolicRateRecord(
                time = Instant.ofEpochMilli(startTime),
                basalMetabolicRate = Power.kilocaloriesPerDay(value),
                zoneOffset = null,
            )

            FLIGHTS_CLIMBED -> FloorsClimbedRecord(
                startTime = Instant.ofEpochMilli(startTime),
                endTime = Instant.ofEpochMilli(endTime),
                floors = value,
                startZoneOffset = null,
                endZoneOffset = null,
            )

            RESPIRATORY_RATE -> RespiratoryRateRecord(
                time = Instant.ofEpochMilli(startTime),
                rate = value,
                zoneOffset = null,
            )
            // AGGREGATE_STEP_COUNT -> StepsRecord()
            BLOOD_PRESSURE_SYSTOLIC -> throw IllegalArgumentException("You must use the [writeBloodPressure] API ")
            BLOOD_PRESSURE_DIASTOLIC -> throw IllegalArgumentException("You must use the [writeBloodPressure] API ")
            WORKOUT -> throw IllegalArgumentException("You must use the [writeWorkoutData] API ")
            else -> throw IllegalArgumentException("The type $type was not supported by the Health plugin or you must use another API ")
        }
        scope.launch {
            try {
                healthConnectClient.insertRecords(listOf(record))
                result.success(true)
            } catch (e: Exception) {
                result.success(false)
            }
        }
    }

    fun writeWorkoutHCData(call: MethodCall, result: Result) {
        val type = call.argument<String>("activityType")!!
        val startTime = Instant.ofEpochMilli(call.argument<Long>("startTime")!!)
        val endTime = Instant.ofEpochMilli(call.argument<Long>("endTime")!!)
        val totalEnergyBurned = call.argument<Int>("totalEnergyBurned")
        val totalDistance = call.argument<Int>("totalDistance")
        val titleStr = call.argument<String>("title")
        val workoutType = workoutTypeMapHealthConnect[type]!!

        var showTitle = ""
        if (titleStr != null) {
            showTitle = titleStr
        } else {
            showTitle = type
        }

        scope.launch {
            try {
                val list = mutableListOf<Record>()
                list.add(
                    ExerciseSessionRecord(
                        startTime = startTime,
                        startZoneOffset = null,
                        endTime = endTime,
                        endZoneOffset = null,
                        exerciseType = workoutType,
                        title = showTitle,
                    ),
                )
                if (totalDistance != null) {
                    list.add(
                        DistanceRecord(
                            startTime = startTime,
                            startZoneOffset = null,
                            endTime = endTime,
                            endZoneOffset = null,
                            distance = Length.meters(totalDistance.toDouble()),
                        ),
                    )
                }
                if (totalEnergyBurned != null) {
                    list.add(
                        TotalCaloriesBurnedRecord(
                            startTime = startTime,
                            startZoneOffset = null,
                            endTime = endTime,
                            endZoneOffset = null,
                            energy = Energy.kilocalories(totalEnergyBurned.toDouble()),
                        ),
                    )
                }
                healthConnectClient.insertRecords(
                    list,
                )
                result.success(true)
                Log.i("FLUTTER_HEALTH::SUCCESS", "[Health Connect] Workout was successfully added!")
            } catch (e: Exception) {
                Log.w(
                    "FLUTTER_HEALTH::ERROR",
                    "[Health Connect] There was an error adding the workout",
                )
                Log.w("FLUTTER_HEALTH::ERROR", e.message ?: "unknown error")
                Log.w("FLUTTER_HEALTH::ERROR", e.stackTrace.toString())
                result.success(false)
            }
        }
    }

    fun writeBloodPressureHC(call: MethodCall, result: Result) {
        val systolic = call.argument<Double>("systolic")!!
        val diastolic = call.argument<Double>("diastolic")!!
        val startTime = Instant.ofEpochMilli(call.argument<Long>("startTime")!!)
        val endTime = Instant.ofEpochMilli(call.argument<Long>("endTime")!!)

        scope.launch {
            try {
                healthConnectClient.insertRecords(
                    listOf(
                        BloodPressureRecord(
                            time = startTime,
                            systolic = Pressure.millimetersOfMercury(systolic),
                            diastolic = Pressure.millimetersOfMercury(diastolic),
                            zoneOffset = null,
                        ),
                    ),
                )
                result.success(true)
                Log.i(
                    "FLUTTER_HEALTH::SUCCESS",
                    "[Health Connect] Blood pressure was successfully added!",
                )
            } catch (e: Exception) {
                Log.w(
                    "FLUTTER_HEALTH::ERROR",
                    "[Health Connect] There was an error adding the blood pressure",
                )
                Log.w("FLUTTER_HEALTH::ERROR", e.message ?: "unknown error")
                Log.w("FLUTTER_HEALTH::ERROR", e.stackTrace.toString())
                result.success(false)
            }
        }
    }

    fun deleteHCData(call: MethodCall, result: Result) {
        val type = call.argument<String>("dataTypeKey")!!
        val startTime = Instant.ofEpochMilli(call.argument<Long>("startTime")!!)
        val endTime = Instant.ofEpochMilli(call.argument<Long>("endTime")!!)
        val classType = MapToHCType[type]!!

        scope.launch {
            try {
                healthConnectClient.deleteRecords(
                    recordType = classType,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime),
                )
                result.success(true)
            } catch (e: Exception) {
                result.success(false)
            }
        }
    }

    val MapSleepStageToType = hashMapOf<Int, String>(
        1 to SLEEP_AWAKE,
        2 to SLEEP_ASLEEP,
        3 to SLEEP_OUT_OF_BED,
        4 to SLEEP_LIGHT,
        5 to SLEEP_DEEP,
        6 to SLEEP_REM,
    )

    val MapToHCType = hashMapOf(
        BODY_FAT_PERCENTAGE to BodyFatRecord::class,
        HEIGHT to HeightRecord::class,
        WEIGHT to WeightRecord::class,
        STEPS to StepsRecord::class,
        AGGREGATE_STEP_COUNT to StepsRecord::class,
        ACTIVE_ENERGY_BURNED to ActiveCaloriesBurnedRecord::class,
        HEART_RATE to HeartRateRecord::class,
        BODY_TEMPERATURE to BodyTemperatureRecord::class,
        BLOOD_PRESSURE_SYSTOLIC to BloodPressureRecord::class,
        BLOOD_PRESSURE_DIASTOLIC to BloodPressureRecord::class,
        BLOOD_OXYGEN to OxygenSaturationRecord::class,
        BLOOD_GLUCOSE to BloodGlucoseRecord::class,
        DISTANCE_DELTA to DistanceRecord::class,
        WATER to HydrationRecord::class,
        SLEEP_ASLEEP to SleepStageRecord::class,
        SLEEP_AWAKE to SleepStageRecord::class,
        SLEEP_LIGHT to SleepStageRecord::class,
        SLEEP_DEEP to SleepStageRecord::class,
        SLEEP_REM to SleepStageRecord::class,
        SLEEP_OUT_OF_BED to SleepStageRecord::class,
        SLEEP_SESSION to SleepSessionRecord::class,
        WORKOUT to ExerciseSessionRecord::class,
        RESTING_HEART_RATE to RestingHeartRateRecord::class,
        BASAL_ENERGY_BURNED to BasalMetabolicRateRecord::class,
        FLIGHTS_CLIMBED to FloorsClimbedRecord::class,
        RESPIRATORY_RATE to RespiratoryRateRecord::class,
    )
}