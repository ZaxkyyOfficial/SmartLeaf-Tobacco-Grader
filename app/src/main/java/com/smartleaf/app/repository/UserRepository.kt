package com.smartleaf.app.repository

import com.smartleaf.app.data.local.UserDao
import com.smartleaf.app.data.local.UserEntity

class UserRepository(private val userDao: UserDao) {
    suspend fun registerUser(user: UserEntity): Long {
        return userDao.insertUser(user)
    }

    suspend fun getUserByEmail(email: String): UserEntity? {
        return userDao.getUserByEmail(email)
    }

    suspend fun getUserById(id: Long): UserEntity? {
        return userDao.getUserById(id)
    }

    suspend fun updateUserVerification(user: UserEntity) {
        userDao.updateUser(user.copy(isVerified = true))
    }

    suspend fun updatePassword(email: String, newPassword: String) {
        userDao.updatePassword(email, newPassword)
    }
}
