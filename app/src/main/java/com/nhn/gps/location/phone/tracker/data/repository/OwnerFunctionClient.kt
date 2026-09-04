package com.nhn.gps.location.phone.tracker.data.repository

import com.google.firebase.functions.FirebaseFunctions
import com.nhn.gps.location.phone.tracker.data.local.InstallationIdentity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Calls backend mutations with proof that belongs only to this installation. */
@Singleton
class OwnerFunctionClient @Inject constructor(
    private val functions: FirebaseFunctions,
    private val installationIdentity: InstallationIdentity,
) {
    suspend fun call(
        functionName: String,
        uid: String,
        values: Map<String, Any?> = emptyMap(),
    ): Any? {
        val ownedUid = installationIdentity.getOrCreateId()
        require(uid.trim() == ownedUid) { "This installation cannot modify another user" }
        val payload = buildMap<String, Any?> {
            put("uid", ownedUid)
            put("ownerSecret", installationIdentity.getOrCreateOwnerSecret())
            putAll(values)
        }
        return functions.getHttpsCallable(functionName).call(payload).await().data
    }
}
