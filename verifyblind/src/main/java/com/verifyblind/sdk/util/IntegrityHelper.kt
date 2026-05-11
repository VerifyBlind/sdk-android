package com.verifyblind.sdk.util

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import kotlinx.coroutines.tasks.await

internal object IntegrityHelper {
    
    private var tokenProvider: StandardIntegrityTokenProvider? = null

    suspend fun prepare(context: Context, cloudProjectNumber: Long) {
        if (tokenProvider != null) return
        try {
            val standardIntegrityManager = IntegrityManagerFactory.createStandard(context)
            val request = PrepareIntegrityTokenRequest.builder()
                .setCloudProjectNumber(cloudProjectNumber)
                .build()
                
            tokenProvider = standardIntegrityManager.prepareIntegrityToken(request).await()
            Log.d("VerifyBlind_SDK", "Play Integrity Provider Prepared")
        } catch (e: Exception) {
            Log.e("VerifyBlind_SDK", "Play Integrity Preparation Failed", e)
            tokenProvider = null
        }
    }

    suspend fun requestIntegrityToken(context: Context, cloudProjectNumber: Long, requestHash: String): String? {
        try {
            if (tokenProvider == null) {
                prepare(context, cloudProjectNumber)
            }
            
            val provider = tokenProvider ?: return null
            
            val tokenRequest = StandardIntegrityTokenRequest.builder()
                .setRequestHash(requestHash)
                .build()
                
            val response = provider.request(tokenRequest).await()
            return response.token()
            
        } catch (e: Exception) {
            Log.e("VerifyBlind_SDK", "Integrity Token Fetch Failed", e)
            return null
        }
    }
}
