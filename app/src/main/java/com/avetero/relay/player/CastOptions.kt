package com.avetero.relay.player

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions as GmsCastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/** Tells the Cast framework to use Google's default media receiver, so no receiver app of our own is needed. */
class CastOptions : OptionsProvider {
    override fun getCastOptions(context: Context): GmsCastOptions =
        GmsCastOptions.Builder().setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID).build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
