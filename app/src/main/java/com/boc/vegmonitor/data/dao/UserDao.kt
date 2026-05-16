package com.boc.vegmonitor.data.dao

import androidx.room.*
import com.boc.vegmonitor.data.entity.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    // 获取当前保存的用户信息（只存一个主账号，所以取第一个即可）
    @Query("SELECT * FROM users LIMIT 1")
    fun getLoggedInUser(): Flow<User?>

    // 插入或更新用户信息
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // 删除用户（注销登录时使用）
    @Query("DELETE FROM users")
    suspend fun clearUser()
}