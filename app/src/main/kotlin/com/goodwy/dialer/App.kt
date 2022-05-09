package com.goodwy.dialer

import android.app.Application
import com.goodwy.commons.extensions.checkUseEnglish
import com.goodwy.dialer.helpers.CallDurationHelper

class App : Application() {
    val callDurationHelper by lazy { CallDurationHelper() }
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
    }
}
