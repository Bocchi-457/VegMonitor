package com.boc.vegmonitor.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore 实例扩展属性
private val Context.loginFailureDataStore: DataStore<Preferences> by preferencesDataStore(name = "login_failures")

/**
 * 登录失败记录数据类
 */
data class LoginFailureRecord(
    val account: String,           // 账号（邮箱/手机号）
    val failureCount: Int,         // 累计失败次数
    val lockLevel: Int,            // 锁定等级（0=未锁定, 1=1分钟, 2=2分钟, 3=5分钟）
    val lastFailureTime: Long,     // 最后一次失败时间戳
    val lockEndTime: Long          // 锁定结束时间戳
)

/**
 * 登录失败记录 Repository
 * 负责持久化存储和读取登录失败记录
 */
class LoginFailureRepository(private val context: Context) {
    
    private val dataStore = context.loginFailureDataStore
    private val RECORDS_KEY = stringPreferencesKey("login_failure_records")
    private val gson = Gson()
    
    /**
     * 获取所有失败记录
     */
    private suspend fun getAllRecords(): Map<String, LoginFailureRecord> {
        return try {
            val preferences = dataStore.data.first()
            val json = preferences[RECORDS_KEY]
            if (json.isNullOrEmpty()) {
                emptyMap()
            } else {
                val type = object : TypeToken<Map<String, LoginFailureRecord>>() {}.type
                gson.fromJson(json, type)
            }
        } catch (e: Exception) {
            Log.e("LoginFailureRepo", "读取失败记录异常", e)
            emptyMap()
        }
    }
    
    /**
     * 获取指定账号的失败记录
     */
    suspend fun getFailureRecord(account: String): LoginFailureRecord? {
        val records = getAllRecords()
        return records[account]
    }
    
    /**
     * 更新或创建失败记录
     */
    suspend fun updateFailureRecord(record: LoginFailureRecord) {
        try {
            dataStore.edit { preferences ->
                val records = getAllRecords().toMutableMap()
                records[record.account] = record
                preferences[RECORDS_KEY] = gson.toJson(records)
                Log.d("LoginFailureRepo", "保存失败记录 - 账号: ${record.account}, 失败次数: ${record.failureCount}, 锁定等级: ${record.lockLevel}")
            }
        } catch (e: Exception) {
            Log.e("LoginFailureRepo", "保存失败记录异常", e)
        }
    }
    
    /**
     * 清除指定账号的失败记录（登录成功后调用）
     */
    suspend fun clearFailureRecord(account: String) {
        try {
            dataStore.edit { preferences ->
                val records = getAllRecords().toMutableMap()
                records.remove(account)
                preferences[RECORDS_KEY] = gson.toJson(records)
                Log.d("LoginFailureRepo", "清除失败记录 - 账号: $account")
            }
        } catch (e: Exception) {
            Log.e("LoginFailureRepo", "清除失败记录异常", e)
        }
    }
    
    /**
     * 检查账号是否处于锁定状态
     * @return 剩余锁定秒数，0表示未锁定
     */
    suspend fun getRemainingLockTime(account: String): Int {
        val record = getFailureRecord(account) ?: return 0
        
        val now = System.currentTimeMillis()
        if (now < record.lockEndTime) {
            val remainingSeconds = ((record.lockEndTime - now) / 1000).toInt()
            Log.d("LoginFailureRepo", "账号 $account 仍在锁定期 - 剩余: ${remainingSeconds}秒")
            return remainingSeconds
        } else {
            Log.d("LoginFailureRepo", "账号 $account 锁定已过期")
            return 0
        }
    }
}
