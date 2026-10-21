package com.dynamicisland.app.util

import android.graphics.Region
import android.view.View
import android.view.ViewTreeObserver
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Uses Android's internal OnComputeInternalInsetsListener via a dynamic reflection proxy
 * to restrict an overlay window's touchable region strictly to the pet's visible shape.
 * All areas outside this region pass touches straight through to the underlying application.
 */
class TouchableRegionHelper(private val targetView: View) {

    private var proxyInstance: Any? = null
    private var addMethod: Method? = null
    private var removeMethod: Method? = null
    private var listenerClass: Class<*>? = null
    private var isRegistered = false

    fun attach(regionProvider: () -> Region?) {
        if (isRegistered) return
        val vto = targetView.viewTreeObserver ?: return

        try {
            listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val insetsInfoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")

            // Field for touchableInsets (int)
            val touchableInsetsField = try {
                insetsInfoClass.getField("touchableInsets")
            } catch (e: NoSuchFieldException) {
                insetsInfoClass.getDeclaredField("mTouchableInsets").apply { isAccessible = true }
            }

            // Field for touchableRegion (Region)
            val touchableRegionField = try {
                insetsInfoClass.getField("touchableRegion")
            } catch (e: NoSuchFieldException) {
                insetsInfoClass.getDeclaredField("touchableRegion").apply { isAccessible = true }
            }

            // Constant for TOUCHABLE_INSETS_REGION = 3
            val touchableInsetsRegionConstant = 3

            val handler = InvocationHandler { _, method, args ->
                if (method.name == "onComputeInternalInsets") {
                    val inoutInfo = args?.getOrNull(0) ?: return@InvocationHandler null
                    val customRegion = regionProvider()
                    if (customRegion != null && !customRegion.isEmpty) {
                        touchableInsetsField.setInt(inoutInfo, touchableInsetsRegionConstant)
                        val internalRegion = touchableRegionField.get(inoutInfo) as? Region
                        internalRegion?.set(customRegion)
                    }
                }
                null
            }

            val proxy = Proxy.newProxyInstance(
                listenerClass!!.classLoader,
                arrayOf(listenerClass!!),
                handler
            )

            addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            removeMethod = ViewTreeObserver::class.java.getMethod("removeOnComputeInternalInsetsListener", listenerClass)

            addMethod?.invoke(vto, proxy)
            proxyInstance = proxy
            isRegistered = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun detach() {
        if (!isRegistered) return
        val vto = targetView.viewTreeObserver
        if (vto?.isAlive == true && proxyInstance != null && removeMethod != null) {
            try {
                removeMethod?.invoke(vto, proxyInstance)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        proxyInstance = null
        isRegistered = false
    }

    fun requestUpdate() {
        if (isRegistered) {
            targetView.post {
                targetView.requestLayout()
            }
        }
    }
}
