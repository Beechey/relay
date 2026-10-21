package com.avetero.relay.data.model

data class Account(val name: String, val server: String, val user: String, val pass: String)

data class AccountInfo(val status: String, val expires: Long?, val maxConnections: Int, val activeConnections: Int)
