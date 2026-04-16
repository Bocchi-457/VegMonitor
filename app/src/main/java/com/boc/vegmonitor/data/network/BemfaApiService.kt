package com.boc.vegmonitor.data.network

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// 巴法云返回的统一数据结构
data class BemfaAuthResponse(
    val code: Int,       // 0 代表成功
    val msg: String?,    // 错误时的提示信息
    val data: LoginData? // 成功时返回的数据对象
)

// 登录返回的数据对象
data class LoginData(
    val code: Int,
    val msg: String?,
    val uid: String?     // 用户私钥
)

// 登录请求参数
data class LoginRequest(
    val email: String,
    val password: String
)

// 手机号登录请求参数
data class PhoneLoginRequest(
    val phone: String,
    val password: String,
    val area: String = "86"  // 默认中国区号
)

// 定义接口
interface BemfaApiService {

    // 邮箱登录
    @POST("vb/api/v1/emailLogin")
    suspend fun login(
        @Body request: LoginRequest
    ): BemfaAuthResponse

    // 手机号登录
    @POST("vb/api/v1/userLogin")
    suspend fun phoneLogin(
        @Body request: PhoneLoginRequest
    ): BemfaAuthResponse

    companion object {
        private const val BASE_URL = "https://apis.bemfa.com/"

        fun create(): BemfaApiService {
            // 创建日志拦截器（仅在调试模式下启用）
            val isDebug = true // TODO: 在生产环境中改为 false 或通过 Application 判断
            
            val loggingInterceptor = HttpLoggingInterceptor { message ->
                // 脱敏处理：隐藏密码字段
                val sanitizedMessage = message
                    .replace(Regex("\"password\"\\s*:\\s*\"[^\"]*\""), "\"password\": \"***\"")
                    .replace(Regex("\"email\"\\s*:\\s*\"[^\"]*\""), "\"email\": \"***\"")
                Log.d("BemfaAPI", sanitizedMessage)
            }.apply {
                // 只在 Debug 模式启用日志
                level = if (isDebug) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            }

            // 创建 OkHttpClient
            val client = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BemfaApiService::class.java)
        }
    }
}
