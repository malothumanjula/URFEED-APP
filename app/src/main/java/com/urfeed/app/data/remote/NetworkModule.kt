package com.urfeed.app.data.remote

import android.content.Context
import com.urfeed.app.data.local.AuthPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class BearerTokenInterceptor(private val preferences: AuthPreferences) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {

        val token = runBlocking {
            preferences.accessToken.first()
        }

        val request = chain.request()
            .newBuilder()
            .apply {
                if (!token.isNullOrBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(request)
    }
}

object NetworkModule {

    // Android Emulator -> Windows PC -> FastAPI backend
    private const val API_BASE_URL = "http://10.0.2.2:8000/"

    fun api(context: Context): ApiService {

        val preferences = AuthPreferences(
            context.applicationContext
        )

        val client = OkHttpClient.Builder()
            .addInterceptor(
                BearerTokenInterceptor(preferences)
            )
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(API_BASE_URL)
            .client(client)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(ApiService::class.java)
    }
}