package com.boc.vegmonitor.data.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

// 巴法云返回的统一数据结构
data class BemfaAuthResponse(
    val code: Int,       // 0 代表成功
    val message: String?, // 错误时的提示信息
    val uid: String?      // 成功时返回的巴法云私钥
)

// 定义接口
interface BemfaApiService {

    // 获取UID（即登录）
    @GET("api/user/uid/")
    suspend fun login(
        @Query("username") username: String,
        @Query("password") password: String
    ): BemfaAuthResponse

    // 注册账号
    @GET("api/user/reg/")
    suspend fun register(
        @Query("username") username: String,
        @Query("password") password: String
    ): BemfaAuthResponse

    companion object {
        private const val BASE_URL = "https://api.bemfa.com/"

        fun create(): BemfaApiService {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BemfaApiService::class.java)
        }
    }
}