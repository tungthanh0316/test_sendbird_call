package com.example.sendbird_flutter_calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import com.sendbird.calls.*
import com.sendbird.calls.SendBirdCall.addListener
import com.sendbird.calls.SendBirdCall.dial
import com.sendbird.calls.handler.*
import io.flutter.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.util.*
import kotlin.math.log


class MainActivity : FlutterActivity() {
    private val METHOD_CHANNEL_NAME = "com.sendbird.calls/method"
    private val ERROR_CODE = "Sendbird Calls"
    private var methodChannel: MethodChannel? = null
    private var directCall: DirectCall? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Setup
        setupChannels(this, flutterEngine.dartExecutor.binaryMessenger)
    }

    override fun onDestroy() {
        disposeChannels()
        super.onDestroy()
    }

    private fun setupChannels(context: Context, messenger: BinaryMessenger) {
        methodChannel = MethodChannel(messenger, METHOD_CHANNEL_NAME)
        methodChannel!!.setMethodCallHandler { call, result ->
            when (call.method) {
                "init" -> {
                    val appId: String? = call.argument("app_id")
                    val userId: String? = call.argument("user_id")
                    val pushToken: String? = call.argument("push_token")

                    when {
                        appId == null -> {
                            result.error(ERROR_CODE, "Failed Init", "Missing app_id")
                        }
                        userId == null -> {
                            result.error(ERROR_CODE, "Failed Init", "Missing user_id")
                        }

                        pushToken == null -> {
                            result.error(ERROR_CODE, "Failed Init", "Missing pushToken")
                        }


                        else -> {

//                            permissions(context)

                            initSendbird(context, appId, pushToken, userId) { successful ->
                                if (!successful) {
                                    print("INITFAIL")
                                    result.error(
                                        ERROR_CODE,
                                        "Failed init",
                                        "Problem initializing Sendbird. Check for valid app_id"
                                    )
                                } else {
                                    print("INITSUCCESS")
                                    result.success(true)
                                }
                            }
                        }
                    }
                }
                "start_direct_call" -> {
                    val calleeId: String? = call.argument("callee_id")
                    if (calleeId == null) {
                        result.error(ERROR_CODE, "Failed call", "Missing callee_id")
                    }
                    var params = DialParams(calleeId!!)
                    params.setCallOptions(CallOptions())
                    directCall = dial(params, object : DialHandler {
                        override fun onResult(call: DirectCall?, e: SendBirdException?) {
                            if (e != null) {
                                result.error(ERROR_CODE, "Failed call", e.message)
                                return
                            }
                            result.success(true)
                        }
                    })
                    directCall?.setListener(object : DirectCallListener() {
                        override fun onEstablished(call: DirectCall) {
                            Log.e("registerPushToken","onEstablished from my side")
                        }
                        override fun onConnected(call: DirectCall) {
                            Log.e("registerPushToken","onConnected from my side")
                        }
                        override fun onEnded(call: DirectCall) {
                            Log.e("registerPushToken","onEnded from my side")
                        }
                    })
                }
                "answer_direct_call" -> {
                    directCall?.accept(AcceptParams())
                }
                "end_direct_call" -> {
                    // End a call
                    directCall?.end();
                    result.success(true);
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    private fun permissions(context: Context) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            ) {
                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT), 10
                )

                // REQUEST_CODE is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }

            return

        }


    }

    private fun initSendbird(
        context: Context,
        appId: String,
        pushToken: String,
        userId: String,
        callback: (Boolean) -> Unit
    ) {
        // Initialize SendBirdCall instance to use APIs in your app.
        if (SendBirdCall.init(context, appId)) {
            // Initialization successful

            // Add event listeners
            addListener(UUID.randomUUID().toString(), object : SendBirdCallListener() {



                override fun onRinging(call: DirectCall) {
                    Log.e("registerPushToken","onRinging")
                    methodChannel?.invokeMethod("direct_call_received") {
                    }

                    val ongoingCallCount = SendBirdCall.ongoingCallCount
                    if (ongoingCallCount >= 2) {
                        call.end()
                        return
                    }

                    call.setListener(object : DirectCallListener() {
                        override fun onEstablished(call: DirectCall) {
                            Log.e("registerPushToken","onEstablished")
                        }

                        override fun onConnected(call: DirectCall) {
                            Log.e("registerPushToken","onConnected")
                            methodChannel?.invokeMethod("direct_call_connected") {
                            }
                        }

                        override fun onEnded(call: DirectCall) {
                            Log.e("registerPushToken","onEnded")
                            val ongoingCallCount = SendBirdCall.ongoingCallCount
                            if (ongoingCallCount == 0) {
//                                CallService.stopService(context)
                            }
                            methodChannel?.invokeMethod("direct_call_ended") {
                            }
                        }

                        override fun onRemoteAudioSettingsChanged(call: DirectCall) {
                            Log.e("registerPushToken","onRemoteAudioSettingsChanged")


                        }

                    })
                }
            })
        }

        // The USER_ID below should be unique to your Sendbird application.
        var params = AuthenticateParams(userId)

        SendBirdCall.authenticate(params, object : AuthenticateHandler {
            override fun onResult(user: User?, e: SendBirdException?) {
                if (e == null) {
                    Log.i("registerPushToken","registerPushToken Réult e==null")
                    SendBirdCall.registerPushToken(pushToken, true, object : CompletionHandler {
                        override fun onResult(e: SendBirdException?) {
                            Log.i("registerPushToken","registerPushToken Réult $e")
                        }
                    })
                    // The user has been authenticated successfully and is connected to Sendbird server.
                    callback(true)
                } else {

                    callback(false)
                }
            }
        })


    }

    private fun disposeChannels() {
        methodChannel!!.setMethodCallHandler(null)
    }
}
