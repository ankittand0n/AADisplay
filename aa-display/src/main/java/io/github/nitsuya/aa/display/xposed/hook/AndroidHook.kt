package io.github.nitsuya.aa.display.xposed.hook

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.IPackageManager
import android.content.res.Configuration
import android.os.Build
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.nitsuya.aa.display.CoreApi
import io.github.nitsuya.aa.display.IsSystemEnv
import io.github.nitsuya.aa.display.xposed.BridgeService
import io.github.nitsuya.aa.display.xposed.CoreManagerService
import io.github.nitsuya.aa.display.xposed.log
import io.github.qauxv.util.Initiator

object AndroidHook : BaseHook() {
    override val tagName: String = "AAD_AndroidHook"
    override fun init(lpparam: XC_LoadPackage.LoadPackageParam) {
        Initiator.init(lpparam.classLoader)
        log(tagName, "xposed init")
        var serviceManagerHook: XC_MethodHook.Unhook? = null
        serviceManagerHook = findMethod("android.os.ServiceManager") {
            name == "addService"
        }.hookBefore { param ->
            if (param.args[0] == "package") {
                serviceManagerHook?.unhook()
                val pms = param.args[1] as IPackageManager
                log(tagName, "Got pms: $pms")
                runCatching {
                    BridgeService.register(pms)
                    log(tagName, "Bridge service injected")
                }.onFailure {
                    log(tagName, "System service crashed", it)
                }
            }
        }

        var activityManagerServiceConstructorHook: List<XC_MethodHook.Unhook> = emptyList()
        activityManagerServiceConstructorHook = findAllConstructors("com.android.server.am.ActivityManagerService") {
            parameterTypes[0] == Context::class.java
        }.hookAfter {
            activityManagerServiceConstructorHook.forEach { hook -> hook.unhook() }
            CoreManagerService.systemContext = it.thisObject.getObjectAs("mUiContext")
            log(tagName, "get systemUiContext")
        }.also {
            if (it.isEmpty())
                log(tagName, "no constructor with parameterTypes[0] == Context found")
        }

        var activityManagerServiceSystemReadyHook: XC_MethodHook.Unhook? = null
        activityManagerServiceSystemReadyHook = findMethod("com.android.server.am.ActivityManagerService") {
            name == "systemReady"
        }.hookAfter {
            activityManagerServiceSystemReadyHook?.unhook()
            CoreManagerService.systemReady()
            log(tagName, "system ready")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {//10+
            var className = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)  //12+
                "com.android.server.wm.ActivityTaskSupervisor"
            else  //10+
                "com.android.server.wm.ActivityStackSupervisor"
            findMethod(className){
                name == "isCallerAllowedToLaunchOnDisplay"
                && parameterCount == 4
                && parameterTypes[0] == Int::class.javaPrimitiveType //callingPid
                && parameterTypes[1] == Int::class.javaPrimitiveType //callingUid
                && parameterTypes[2] == Int::class.javaPrimitiveType //launchDisplayId
                && parameterTypes[3] == ActivityInfo::class.java
            }.hookAfter { param ->
                if((param.result as Boolean).not() && param.args[2] == CoreManagerService.getDisplayId()){
                    param.result = true
                    log(tagName,"hook isCallerAllowedToLaunchOnDisplay success")
                }
            }
        }

    }

    object Power {
        /** true = 3rd param is Boolean (AOSP), false = 3rd param is Int (some ROMs) */
        private var nonInteractiveArgIsBoolean = true
        private val powerPress by lazy {
            if(!IsSystemEnv) return@lazy null
            // Try AOSP signature first: powerPress(long, int, boolean)
            try{
                val m = findMethod("com.android.server.policy.PhoneWindowManager") {
                    name == "powerPress"
                            && parameterCount == 3
                            && parameterTypes[0] == Long::class.javaPrimitiveType
                            && parameterTypes[1] == Int::class.javaPrimitiveType
                            && parameterTypes[2] == Boolean::class.javaPrimitiveType
                }
                nonInteractiveArgIsBoolean = true
                log(tagName, "Power: using powerPress(long, int, boolean) signature")
                return@lazy m
            } catch (_: Throwable) {}
            // Fallback: powerPress(long, int, int) — some ROMs use int instead of boolean
            try{
                val m = findMethod("com.android.server.policy.PhoneWindowManager") {
                    name == "powerPress"
                            && parameterCount == 3
                            && parameterTypes[0] == Long::class.javaPrimitiveType
                            && parameterTypes[1] == Int::class.javaPrimitiveType
                            && parameterTypes[2] == Int::class.javaPrimitiveType
                }
                nonInteractiveArgIsBoolean = false
                log(tagName, "Power: using powerPress(long, int, int) signature")
                return@lazy m
            } catch (e: Throwable){
                log(tagName, "Power: could not find any powerPress signature", e)
                null
            }
        }
        private var hookPower : XC_MethodHook.Unhook? = null
        fun hook(){
            unHook()
            hookPower = powerPress?.hookBefore {
                val beganFromNonInteractive = if (nonInteractiveArgIsBoolean) {
                    it.args[2] as Boolean
                } else {
                    (it.args[2] as Int) != 0
                }
                if (!beganFromNonInteractive) {
                    CoreApi.toggleDisplayPower()
                    it.abortMethod()
                } else {
                    CoreApi.displayPower(true)
                }
            }
        }
        fun unHook(){
            hookPower?.unhook()
            hookPower = null
        }
    }
    object FuckAppUseApplicationContext {
        private val appInitUseDisplay: HashMap<String, Int> = hashMapOf()
        private val activityTaskManagerService_startProcessAsync by lazy {
            if(!IsSystemEnv) return@lazy null
            try{
                findMethod("com.android.server.wm.ActivityTaskManagerService"){
                    name == "startProcessAsync"
                }
            } catch (e: Throwable){
                log(tagName,  "FuckAppUseAppContext ActivityTaskManagerService.startProcessAsync method", e)
                null
            }
        }
        private val applicationThread_bindApplication by lazy {
            if(!IsSystemEnv) return@lazy null
            try{
                findMethod("android.app.IApplicationThread\$Stub\$Proxy"){
                    name == "bindApplication"
                }
            } catch (e: Throwable){
                log(tagName,  "FuckAppUseAppContext IApplicationThread.bindApplication method", e)
                null
            }
        }

        private var activityTaskManagerService_startProcessAsync_hook : XC_MethodHook.Unhook? = null
        private var applicationThread_bindApplication_hook : XC_MethodHook.Unhook? = null
        fun hook(){
            unHook()
            activityTaskManagerService_startProcessAsync_hook  = activityTaskManagerService_startProcessAsync?.hookBefore { param ->
                try {
                    val activityRecord = param.args[0]
                    val displayId = activityRecord.invokeMethod("getDisplayId") as Int
                    val packageName = activityRecord.getObject("packageName") as String
                    if(displayId == 0){
                        if(appInitUseDisplay.containsKey(packageName)){
                            appInitUseDisplay.remove(packageName)
                        }
                        return@hookBefore
                    }
                    appInitUseDisplay[packageName] = displayId
                } catch (e: Exception) {
                    log(tagName, "activityTaskManagerService_startProcessAsync Hook Exception", e)
                }
            }
            applicationThread_bindApplication_hook = applicationThread_bindApplication?.hookBefore { param ->
                try {
                    val configuration = param.args[15]
                    if(configuration !is Configuration){
                        return@hookBefore
                    }
                    val packageName = (param.args[0] as String).run {
                        this.substringBeforeLast(":")
                    }
                    if(appInitUseDisplay.containsKey(packageName)){
                        val densityDpi = CoreManagerService.getDensityDpi()
                        if(densityDpi != 0){
                            configuration.densityDpi = densityDpi
                        }
                    }
                } catch (e: Exception) {
                    log(tagName, "applicationThread_bindApplication Hook Exception", e)
                }
            }
        }

        fun unHook(){
            appInitUseDisplay.clear()
            activityTaskManagerService_startProcessAsync_hook?.apply { unhook() }
            activityTaskManagerService_startProcessAsync_hook = null

            applicationThread_bindApplication_hook?.apply { unhook() }
            applicationThread_bindApplication_hook = null
        }

    }

    /**
     * Prevents the AADisplay virtual display from entering DOZE/OFF state when
     * the phone screen turns off or enters AOD. Hooks requestDisplayState() to
     * intercept non-ON requests for the virtual display and keep it ON.
     */
    object VirtualDisplayPower {
        private val hooks = mutableListOf<XC_MethodHook.Unhook>()

        fun hook() {
            unHook()
            val hostClassLoader = Initiator.getHostClassLoader()
            if (hostClassLoader == null) {
                log(tagName, "VirtualDisplayPower: hostClassLoader is null, cannot hook")
                return
            }

            // Hook 1: VirtualDisplayDevice.requestDisplayStateLocked — keeps device state ON
            val deviceTargets = listOf(
                "com.android.server.display.VirtualDisplayAdapter\$VirtualDisplayDevice",
                "com.android.server.display.DisplayDevice"
            )
            for (className in deviceTargets) {
                try {
                    val clazz = hostClassLoader.loadClass(className)
                    val method = clazz.declaredMethods.firstOrNull { m ->
                        m.name == "requestDisplayStateLocked"
                                && m.parameterTypes.isNotEmpty()
                                && m.parameterTypes[0] == Int::class.javaPrimitiveType
                    }
                    if (method != null) {
                        hooks.add(XposedBridge.hookMethod(method, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    val uniqueId = param.thisObject.invokeMethodAutoAs<String>("getUniqueId")
                                    if (uniqueId != null && uniqueId.contains("AADisplay")) {
                                        val requestedState = param.args[0] as Int
                                        if (requestedState != 2) {
                                            log(tagName, "VirtualDisplayPower[Device]: intercepted state=$requestedState, forcing ON")
                                            param.args[0] = 2
                                            for (i in 1 until param.args.size) {
                                                if (param.args[i] is Float) param.args[i] = 1.0f
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        }))
                        log(tagName, "VirtualDisplayPower: hooked $className.requestDisplayStateLocked (${method.parameterTypes.size} params)")
                        break
                    }
                } catch (e: Throwable) {
                    log(tagName, "VirtualDisplayPower: failed to hook $className", e)
                }
            }

            // Hook 2: LogicalDisplay — intercept the display state that WM/InputDispatcher sees
            // First enumerate methods to find the right one, then hook getDisplayInfoLocked
            // to patch the state field in the returned DisplayInfo
            try {
                val logicalDisplayClass = hostClassLoader.loadClass("com.android.server.display.LogicalDisplay")
                
                // Log all methods for debugging
                val allMethods = logicalDisplayClass.declaredMethods.map { "${it.name}(${it.parameterTypes.joinToString { p -> p.simpleName }})" }
                log(tagName, "VirtualDisplayPower: LogicalDisplay methods: ${allMethods.joinToString()}")

                // Try requestDisplayStateLocked first
                val rdslMethod = logicalDisplayClass.declaredMethods.firstOrNull { m ->
                    m.name == "requestDisplayStateLocked"
                }
                if (rdslMethod != null) {
                    hooks.add(XposedBridge.hookMethod(rdslMethod, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            try {
                                val displayId = param.thisObject.invokeMethodAutoAs<Int>("getDisplayIdLocked")
                                if (displayId != null && displayId == aaDisplayId) {
                                    val state = param.args[0] as Int
                                    if (state != 2) {
                                        log(tagName, "VirtualDisplayPower[LogicalDisplay]: displayId=$displayId intercepted state=$state, forcing ON")
                                        param.args[0] = 2
                                        for (i in 1 until param.args.size) {
                                            if (param.args[i] is Float) param.args[i] = 1.0f
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }))
                    log(tagName, "VirtualDisplayPower: hooked LogicalDisplay.requestDisplayStateLocked")
                }
                
                // Also hook getDisplayInfoLocked to patch the state in returned DisplayInfo
                val gdiMethod = logicalDisplayClass.declaredMethods.firstOrNull { m ->
                    m.name == "getDisplayInfoLocked"
                }
                if (gdiMethod != null) {
                    hooks.add(XposedBridge.hookMethod(gdiMethod, object : XC_MethodHook() {
                        override fun afterHookedMethod(param: MethodHookParam) {
                            try {
                                val displayId = param.thisObject.invokeMethodAutoAs<Int>("getDisplayIdLocked")
                                if (displayId != null && displayId == aaDisplayId && param.result != null) {
                                    val displayInfo = param.result
                                    val stateField = displayInfo!!.javaClass.getField("state")
                                    val currentState = stateField.getInt(displayInfo)
                                    if (currentState != 2) {
                                        stateField.setInt(displayInfo, 2) // Force ON
                                        log(tagName, "VirtualDisplayPower[DisplayInfo]: patched state=$currentState→ON for displayId=$displayId")
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }))
                    log(tagName, "VirtualDisplayPower: hooked LogicalDisplay.getDisplayInfoLocked")
                }
            } catch (e: Throwable) {
                log(tagName, "VirtualDisplayPower: failed to hook LogicalDisplay", e)
            }

            if (hooks.isEmpty()) {
                log(tagName, "VirtualDisplayPower: no hooks installed")
            }
        }

        private var aaDisplayId: Int = -1

        fun setAADisplayId(displayId: Int) {
            aaDisplayId = displayId
            log(tagName, "VirtualDisplayPower: tracked AADisplay displayId=$displayId")
        }

        fun unHook() {
            hooks.forEach { it.unhook() }
            hooks.clear()
            aaDisplayId = -1
        }
    }
}