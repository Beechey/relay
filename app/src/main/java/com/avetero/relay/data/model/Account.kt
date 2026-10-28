package com.avetero.relay.data.model

data class Account(val name: String, val server: String, val user: String, val pass: String)

/** [timezone] is the provider's own (from server_info); catch-up start times are written in it. */
data class AccountInfo(val status: String, val expires: Long?, val maxConnections: Int, val activeConnections: Int, val timezone: String = "UTC")
