package com.example.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLockSecurityTest {

    @Test
    fun saltedPasswordHashVerifiesCorrectPasswordAndRejectsWrongOnes() {
        val (salt1, hash1) = AppLockSecurity.createSaltedHash("secret123")
        val (salt2, hash2) = AppLockSecurity.createSaltedHash("secret123")

        assertNotEquals(salt1, salt2)
        assertNotEquals(hash1, hash2)

        assertTrue(AppLockSecurity.verifyPassword("secret123", salt1, hash1))
        assertTrue(AppLockSecurity.verifyPassword("secret123", salt2, hash2))
        assertFalse(AppLockSecurity.verifyPassword("wrong", salt1, hash1))
        assertFalse(AppLockSecurity.verifyPassword("", salt1, hash1))
        assertFalse(AppLockSecurity.verifyPassword("secret123", "", hash1))
    }
}
