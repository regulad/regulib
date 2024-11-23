package xyz.regulad.regulib.compose

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.serialization.decodeArguments
import androidx.navigation.toRoute
import kotlinx.serialization.serializer
import kotlin.reflect.KClass
import kotlin.reflect.full.createType

/**
 * This is an alternative to [NavBackStackEntry.toRoute] that uses reflection to instantiate the route class instead of passing the class via a refiled type parameter.
 *
 * In order for this function to work, the route class must be fully qualified and accessible at runtime via [Class.forName].
 *
 * @return The route object if the destination has a route and the type is accessible, otherwise `null`
 */
@SuppressLint("RestrictedApi")
@Suppress("Unused")
fun <T : Any> NavBackStackEntry.toRoute(): T? {
    val routeFullyQualifiedName = destination.route?.substringBefore("/")
    val routeKClass = routeFullyQualifiedName?.let { Class.forName(it).kotlin as KClass<T> } ?: return null

    // we can't reflect into the inline androidx.navigation.toRoute function because it doesn't exist at runtime, it is literally inlined

    // the following code is copied from the official implementation of toRoute
    val bundle = arguments ?: Bundle()
    val typeMap = destination.arguments.mapValues { it.value.type }
    return serializer(routeKClass.createType()).decodeArguments(bundle, typeMap) as T
}
