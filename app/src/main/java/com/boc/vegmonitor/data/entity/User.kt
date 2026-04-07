package com.boc.vegmonitor.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String = "",
    val bemfaUid: String = "", // 这里存储巴法云的私钥 (UID)
    val loginTime: Long = System.currentTimeMillis() // 记录登录时间
)