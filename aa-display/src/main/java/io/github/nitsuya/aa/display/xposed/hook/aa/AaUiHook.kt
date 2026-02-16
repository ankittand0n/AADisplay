package io.github.nitsuya.aa.display.xposed.hook.aa

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.github.kyuubiran.ezxhelper.init.InitFields
import com.github.kyuubiran.ezxhelper.utils.argTypes
import com.github.kyuubiran.ezxhelper.utils.findConstructor
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.getIdByName
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNull
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import com.github.kyuubiran.ezxhelper.utils.loadClass
import com.github.kyuubiran.ezxhelper.utils.staticMethod
import de.robv.android.xposed.callbacks.XC_LoadPackage
import de.robv.android.xposed.callbacks.XCallback
import io.github.nitsuya.aa.display.BuildConfig
import io.github.nitsuya.aa.display.R
import io.github.nitsuya.aa.display.service.AaActivityService
import io.github.nitsuya.aa.display.util.AABroadcastConst
import io.github.nitsuya.aa.display.util.AADisplayConfig
import io.github.nitsuya.aa.display.xposed.hook.AaHook
import io.github.nitsuya.aa.display.xposed.hook.abortMethod
import io.github.nitsuya.aa.display.xposed.log
import io.github.nitsuya.template.bases.runMain
import io.github.qauxv.ui.CommonContextWrapper
import kotlinx.coroutines.delay
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Constructor
import java.lang.reflect.Method

object AaUiHook: AaHook() {
    override val tagName: String = "AAD_AaUiHook"

    private var layoutInfoConstructor: Constructor<*>? = null
    private var startMethod: Method? = null

    private var resLayoutLeftResourceId: Int = 0
    private var resLayoutRightResourceId: Int = 0

    private var resLayoutGhFacetBarId: Int = 0
    private var resIdStatusBarId: Int = 0
    private var resIdAssistantIconContainerId: Int = 0
    private var resIdAssistantIconId: Int = 0
    private var resIdLauncherAndDashboardIconContainerId: Int = 0
    private var resIdLauncherAndDashboardIconId: Int = 0

    override fun isSupportProcess(processName: String): Boolean {
        return processProjection == processName
    }

    private fun hookLayoutInfo(bridge: DexKitBridge): Boolean {
        return try {
            val classes = bridge.findClass {
                searchPackages = listOf("")
                matcher {
                    usingStrings {
                        add(
                            "LayoutInfo{layoutResourceId=",
                            StringMatchType.StartsWith,
                            false
                        )
                    }
                }
            }
            if (classes.isEmpty() || classes.size > 1) {
                throw NoSuchMethodException("AaUiHook: not found LayoutInfo class: ${classes.size}")
            }
            layoutInfoConstructor = findConstructor(classes[0].name) {
                // Support both 8-arg and 9-arg signatures
                (parameterCount == 8 || parameterCount == 9)
                        && parameterTypes[0] == Int::class.javaPrimitiveType       //layoutResourceId
                        && parameterTypes[1] == Int::class.javaPrimitiveType       //displayWidthDp
                        && parameterTypes[2] == Int::class.javaPrimitiveType       //displayHeightDp
                        && parameterTypes[3] == Int::class.javaPrimitiveType       //layoutType
                        && parameterTypes[4] == Boolean::class.javaPrimitiveType   //isRightHandDrive
                        && parameterTypes[5] == Boolean::class.javaPrimitiveType   //hasVerticalRail
            }
            true
        } catch (t: Throwable) {
            log(tagName, "Failed to hook LayoutInfo", t)
            false
        }
    }

    override fun loadDexClass(bridge: DexKitBridge, lpparam: XC_LoadPackage.LoadPackageParam) {
        if (!hookLayoutInfo(bridge)) {
            return
        }

        try{
            startMethod = loadClass("com.google.android.projection.gearhead.service.CarSystemUiControllerService").staticMethod("a", null, argTypes(Intent::class.java))
        } catch (e: Throwable){
            log(tagName,  "AaUiHook: not found CarSystemUiControllerService.a static method", e)
        }

        resLayoutGhFacetBarId = InitFields.appContext.resources.getIdentifier("gh_coolwalk_vertical_facet_bar", "layout", InitFields.appContext.packageName)
        resIdStatusBarId = getIdByName("status_bar")//android.support.p001v4.app.FragmentContainerView
        resIdAssistantIconContainerId = getIdByName("assistant_icon_container")//com.google.android.apps.auto.components.coolwalk.focusring.FocusInterceptor
        resIdAssistantIconId = getIdByName("assistant_icon")//com.google.android.apps.auto.components.coolwalk.button.CoolwalkButton
        resIdLauncherAndDashboardIconContainerId = getIdByName("launcher_and_dashboard_icon_container")//com.google.android.apps.auto.components.coolwalk.focusring.FocusInterceptor
        resIdLauncherAndDashboardIconId = getIdByName("launcher_and_dashboard_icon")//com.google.android.apps.auto.components.coolwalk.button.CoolwalkButton

        resLayoutLeftResourceId = InitFields.appContext.resources.getIdentifier("sys_ui_layout_canonical_vertical_rail_lhd", "layout", InitFields.appContext.packageName)
        resLayoutRightResourceId = InitFields.appContext.resources.getIdentifier("sys_ui_layout_canonical_vertical_rail_rhd", "layout", InitFields.appContext.packageName)

        check(resLayoutGhFacetBarId != 0) { "resLayoutGhFacetBarId not found" }
        if (resIdStatusBarId == 0) log(tagName, "Warning: resIdStatusBarId not found")
        if (resIdAssistantIconContainerId == 0) log(tagName, "Warning: resIdAssistantIconContainerId not found")
        if (resIdAssistantIconId == 0) log(tagName, "Warning: resIdAssistantIconId not found")
        if (resIdLauncherAndDashboardIconContainerId == 0) log(tagName, "Warning: resIdLauncherAndDashboardIconContainerId not found")
        if (resIdLauncherAndDashboardIconId == 0) log(tagName, "Warning: resIdLauncherAndDashboardIconId not found")

        if (resLayoutLeftResourceId == 0) log(tagName, "Warning: resLayoutLeftResourceId not found")
        if (resLayoutRightResourceId == 0) log(tagName, "Warning: resLayoutRightResourceId not found")
    }

    override fun hook(config: SharedPreferences, lpparam: XC_LoadPackage.LoadPackageParam) {
        log(tagName,  "AaUiHook: ~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        hookBaseClick()
        hookLayout()
        hookFacetBar(config)
        hookRadius(config)
    }

    private fun printBundle(extras: Bundle, index: Int): String{
        val keys = extras.keySet()
        return keys.joinToString { it } + " \r\n " + keys.mapNotNull { key ->
            val value = extras.get(key)
            if (value == null) return@mapNotNull "$key -> null"
            if (value is Bundle) {
                "$key -> Type:Bundle, ${printBundle(value, index + 1)}"
            } else {
                "$key -> ${value}, Type:${value::class.java.name}"
            }
        }.joinToString(separator = "\r\n", prefix = "    ".repeat(index)) { it }
    }

    private fun hookLayout() {
        if (layoutInfoConstructor == null) {
            return
        }
        layoutInfoConstructor?.hookAfter { param -> log(tagName, param.thisObject.toString()) }
        layoutInfoConstructor?.hookBefore { param ->
            when(param.args[3] as Int){ //layoutType
                8,9,10 -> return@hookBefore;
            }
            var isRightHandDrive = param.args[4] as Boolean // isRightHandDrive left false, right:true
            param.args[0] = if(isRightHandDrive) resLayoutRightResourceId else resLayoutLeftResourceId
            param.args[3] = if(isRightHandDrive) 4 else 3//layoutType left:3, right:4
            param.args[5] = true //hasVerticalRail
        }
    }

    private fun hookFacetBar(config: SharedPreferences) {
        val closeLauncherDashboard = AADisplayConfig.CloseLauncherDashboard.get(config)
        val autoOpen = AADisplayConfig.AutoOpen.get(config)
        findMethod(LayoutInflater::class.java) {
            name == "inflate"
            && parameterCount == 3
            && parameterTypes[0] == Int::class.javaPrimitiveType // resource
            && parameterTypes[1] == ViewGroup::class.java // root
            && parameterTypes[2] == Boolean::class.javaPrimitiveType // attachToRoot
        }.hookAfter { param ->
            if (param.args[0] as Int != resLayoutGhFacetBarId) {
                return@hookAfter
            }
            val resultViewGroup = param.result as ViewGroup? ?: return@hookAfter //androidx.constraintlayout.widget.ConstraintLayout
            val ctx = (param.thisObject as LayoutInflater).context
            val ctx2 = CommonContextWrapper.createAppCompatContext(ctx)
            val layoutInflater = LayoutInflater.from(ctx2)
            val resultViewGroupParent = (resultViewGroup.parent as ViewGroup?)?.apply {
                removeView(resultViewGroup)
            }
            if(closeLauncherDashboard){
                resultViewGroup.findViewById<View>(resIdLauncherAndDashboardIconId).apply {
                    setOnClickFinallyListener {
                        performLongClick()
                    }
                }
            }
            val aaFacetBar = layoutInflater.inflate(R.layout.aa_facet_bar, resultViewGroupParent, false) as ConstraintLayout
            if(autoOpen){
                aaFacetBar.post {
                    runMain {
                        delay(1000)
                        try{
                            startMethod?.invoke(null, Intent().apply {
                                component = ComponentName(BuildConfig.APPLICATION_ID, AaActivityService::class.java.name)
                                putExtra("android.intent.extra.PACKAGE_NAME", BuildConfig.APPLICATION_ID)
                            })
                        } catch (e: Throwable) {
                            log(tagName, "CarSystemUiControllerService.a start app error", e)
                        }
                    }
                }
            }
            val createBtn: (resId: Int, block: View.() -> Unit) -> Int = { resId, block ->
                val btn = ImageView(ctx).apply {
                    id = View.generateViewId()
                    layoutParams = ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setImageResource(resId)
                }
                block(btn)
                aaFacetBar.addView(btn)
                btn.id
            }
            val topIds = arrayListOf(
                resIdStatusBarId,
                resIdLauncherAndDashboardIconContainerId
            )
            val bottomIds = arrayListOf(
                createBtn(R.drawable.ic_aa_arrow_back_44){
                    val intentClick = Intent().apply {
                        action = AABroadcastConst.ACTION_SCREEN_CONTROL
                        putExtra(AABroadcastConst.EXTRA_ACTION, KeyEvent.KEYCODE_BACK)
                    }
                    setOnClickListener {
                        ctx.sendBroadcast(intentClick)
                    }
                    setPadding(0, 5, 0, 5)
                },
                createBtn(R.drawable.ic_aa_home_44){
                    val intentClick = Intent().apply {
                        action = AABroadcastConst.ACTION_SCREEN_CONTROL
                        putExtra(AABroadcastConst.EXTRA_ACTION, KeyEvent.KEYCODE_HOME)
                    }
                    setOnClickListener {
                        ctx.sendBroadcast(intentClick)
                    }
                    setPadding(0, 5, 0, 5)
                },
                createBtn(R.drawable.ic_aa_fullscreen_44){
                    val intentClick = Intent().apply {
                        action = AABroadcastConst.ACTION_SCREEN_CONTROL
                        putExtra(AABroadcastConst.EXTRA_ACTION, KeyEvent.KEYCODE_APP_SWITCH)
                    }
                    setOnClickListener {
                        ctx.sendBroadcast(intentClick)
                    }
                    setPadding(0, 5, 0, 5)
                }
            )
            arrayListOf(resIdStatusBarId, resIdLauncherAndDashboardIconContainerId).forEach { vId ->
                val view = resultViewGroup.findViewById<View>(vId)
                (view.parent as ViewGroup?)?.apply {
                    removeView(view)
                }
                aaFacetBar.addView(view)
            }
//            val statusBarOverlayId = View(ctx).run {
//                id = View.generateViewId()
//                layoutParams = ConstraintLayout.LayoutParams(0, 0)
//                val intentClick = Intent().apply {
//                    action = AABroadcastConst.ACTION_SCREEN_CONTROL
//                    putExtra(AABroadcastConst.EXTRA_ACTION, KeyEvent.KEYCODE_POWER)
//                }
//                setOnClickListener {
//                    ctx.sendBroadcast(intentClick)
//                }
//                val statusBar = aaFacetBar.findViewById<ViewGroup>(resIdStatusBarId)
//                setOnLongClickListener {
//                    if(statusBar.childCount > 0){
//                        statusBar.getChildAt(0).performClick()
//                    }
//                    true
//                }
//                aaFacetBar.addView(this)
//                id
//            }
            val set = ConstraintSet()
            set.clone(aaFacetBar)
            bottomIds.forEachIndexed { index, vId ->
                set.connect(vId, ConstraintSet.BOTTOM, if(index == 0) ConstraintSet.PARENT_ID else bottomIds[index-1], if(index == 0) ConstraintSet.BOTTOM else ConstraintSet.TOP, 0)
                set.connect(vId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.connect(vId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
            }
            topIds.forEachIndexed { index, vId ->
                set.connect(vId, ConstraintSet.TOP, if(index == 0) ConstraintSet.PARENT_ID else topIds[index-1], if(index == 0) ConstraintSet.TOP else ConstraintSet.BOTTOM, 0)
                set.connect(vId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0)
                set.connect(vId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
            }
//            set.connect(statusBarOverlayId, ConstraintSet.BOTTOM, resIdStatusBarId, ConstraintSet.BOTTOM, 0)
//            set.connect(statusBarOverlayId, ConstraintSet.TOP, resIdStatusBarId, ConstraintSet.TOP, 0)
//            set.connect(statusBarOverlayId, ConstraintSet.END, resIdStatusBarId, ConstraintSet.END, 0)
//            set.connect(statusBarOverlayId, ConstraintSet.START, resIdStatusBarId, ConstraintSet.START, 0)
            set.applyTo(aaFacetBar)
            resultViewGroup.visibility = View.GONE
            aaFacetBar.addView(resultViewGroup)
            param.result = aaFacetBar
        }
    }

    private fun hookBaseClick() {
        try {
            findMethod(View::class.java) {
                name == "setOnLongClickListener"
                && parameterCount == 1
                && parameterTypes[0] == View.OnLongClickListener::class.java
            }.hookBefore(XCallback.PRIORITY_LOWEST) {
                if (it.args[0] is FinallyListener) return@hookBefore
                val view = it.thisObject as View
                if (!view.hasOnLongClickListeners() || (view.getObjectOrNull("mListenerInfo")?.getObjectOrNull("mOnLongClickListener") is FinallyListener).not()) {
                    return@hookBefore
                }
                view.setOnOriLongClickListener(it.args[0] as View.OnLongClickListener)
                it.abortMethod()
            }
        } catch (e: Throwable) {
            log(tagName, "hook View.setOnLongClickListener", e)
        }
        try {
            findMethod(View::class.java) {
                name == "setOnClickListener"
                && parameterCount == 1
                && parameterTypes[0] == View.OnClickListener::class.java
            }.hookBefore(XCallback.PRIORITY_LOWEST) {
                if (it.args[0] is FinallyListener) return@hookBefore
                val view = it.thisObject as View
                if (!view.hasOnClickListeners() || (view.getObjectOrNull("mListenerInfo")?.getObjectOrNull("mOnClickListener") is FinallyListener).not()) {
                    return@hookBefore
                }
                view.setOnOriClickListener(it.args[0] as View.OnClickListener)
                it.abortMethod()
            }
        } catch (e: Throwable) {
            log(tagName, "hook View.setOnClickListener", e)
        }
    }

    private fun hookRadius(config: SharedPreferences) {
        if (!AADisplayConfig.ForceRightAngle.get(config)) {
            return
        }
        try {
            try {
                findConstructor("com.google.android.gms.car.ProjectionWindowDecorationParams") {
                    parameterCount == 9
                            && parameterTypes[0] == Int::class.javaPrimitiveType
                            && parameterTypes[1] == Int::class.javaPrimitiveType
                            && parameterTypes[2] == Int::class.javaPrimitiveType
                            && parameterTypes[3] == Int::class.javaPrimitiveType
                            && parameterTypes[4] == Int::class.javaPrimitiveType
                            && parameterTypes[5] == Int::class.javaPrimitiveType //cornerRadius
                            && parameterTypes[6] == Int::class.javaPrimitiveType
                            && parameterTypes[7] == Boolean::class.javaPrimitiveType
                            && parameterTypes[8] == Boolean::class.javaPrimitiveType
                }.hookBefore { param ->
                    param.args[5] = 0
                }
                return
            } catch (e: Exception) {
                log(tagName, "Strict 9-arg constructor not found, trying fallback...")
            }

            try {
                findConstructor("com.google.android.gms.car.ProjectionWindowDecorationParams") {
                    parameterCount == 9 || parameterCount == 10
                }.hookBefore { param ->
                    try {
                        param.args[5] = 0
                    } catch (e: Throwable) {
                        log(tagName, "Failed to set radius at index 5", e)
                    }
                }
                return
            } catch (e: Exception) {
                log(tagName, "9/10-arg constructor not found, trying 4-arg...")
            }

            try {
                findConstructor("com.google.android.gms.car.ProjectionWindowDecorationParams") {
                    parameterCount == 4
                }.hookBefore { param ->
                    try {
                        param.args[3] = 0f
                    } catch (_: Throwable) {
                        try {
                            param.args[3] = 0
                        } catch (_: Throwable) {
                        }
                    }
                }
            } catch (e: Exception) {
                log(tagName, "Failed to find ProjectionWindowDecorationParams for radius hook: ${e.message}")
            }
        } catch (e: Throwable) {
            log(tagName, "Unexpected error in hookRadius: ${e.message}", e)
        }
    }

    interface FinallyListener
    private fun interface OnClickFinallyListener: View.OnClickListener, FinallyListener
    private fun interface OnLongClickFinallyListener: View.OnLongClickListener, FinallyListener
    private fun View.setOnClickFinallyListener(l: OnClickFinallyListener) = this.setOnClickListener(l)
    private fun View.setOnLongClickFinallyListener(l: OnLongClickFinallyListener) = this.setOnLongClickListener(l)
    private fun View.setOnOriClickListener(l: View.OnClickListener) = this.setTag(R.id.ori_click_listener, l)
    private fun View.setOnOriLongClickListener(l: View.OnLongClickListener) = this.setTag(R.id.ori_long_click_listener, l)
    private fun View.performOriClick() {
        val clickListener = this.getTag(R.id.ori_click_listener) as View.OnClickListener? ?: return
        clickListener.onClick(this)
    }
    private fun View.performOriLongClick(): Boolean {
        val clickListener = this.getTag(R.id.ori_long_click_listener) as View.OnLongClickListener? ?: return false
        return clickListener.onLongClick(this)
    }
}
